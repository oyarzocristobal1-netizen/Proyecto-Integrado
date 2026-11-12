package com.example.myapplication.data

import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.detectAlerts
import com.example.myapplication.model.QualityLevel
import com.example.myapplication.model.UserSession
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Caja de sensores registrada en Firestore (coleccion "dispositivos"). */
const val DEFAULT_DEVICE_ID = "caja-01"

/** Cuantas mediciones muestra el historial (controla el consumo de lecturas, informe 3.10). */
const val HISTORY_LIMIT = 50L

/** Lote: la leche de un estanque en un dia (informe, Tabla 20: un lote por estanque y dia). */
data class Lot(
    val id: String,
    val tank: String,
    val date: Date,
    val createdBy: String,
    val createdByName: String,
    val note: String
)

/** Medicion guardada (inmutable). */
data class SavedMeasurement(
    val id: String,
    val dateTime: Date,
    val lotId: String,
    val deviceId: String,
    val milkerUid: String,
    val milkerName: String,
    val temperature: Double,
    val ph: Double,
    val conductivity: Double,
    val turbidity: Double,
    /** verde, ambar o rojo */
    val status: String,
    val title: String
)

/** Alerta guardada (coleccion "alertas"). Solo el campo atendida se puede cambiar. */
data class MilkAlert(
    val id: String,
    val measurementId: String,
    val lotId: String,
    val type: String,
    val severity: AlertSeverity,
    val date: Date,
    val attended: Boolean,
    val value: Double,
    val message: String,
    val milkerName: String
)

/**
 * Lotes y mediciones en Cloud Firestore. Firestore guarda una copia local en el telefono:
 * sin conexion las escrituras quedan en cola y se suben solas al volver la red.
 */
class RecordsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val lots = db.collection("lotes")
    private val measurements = db.collection("mediciones")
    private val alerts = db.collection("alertas")

    // ------------------------------------------------------------------ lotes

    /** Lotes recientes, del mas nuevo al mas antiguo, actualizados en tiempo real. */
    fun listenRecentLots(
        onChange: (List<Lot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        lots.orderBy("fecha", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.mapNotNull { it.toLot() })
                }
            }

    /**
     * Devuelve el lote de hoy para ese estanque, o lo crea. El ID es fecha + estanque,
     * asi no se duplica aunque dos personas lo creen a la vez.
     */
    suspend fun getOrCreateTodayLot(
        tank: String,
        session: UserSession,
        onError: (Exception) -> Unit
    ): Lot {
        val cleanTank = tank.trim()
        val now = Date()
        val id = lotIdFor(cleanTank, now)
        val existing = runCatching { lots.document(id).get().await() }.getOrNull()
        if (existing != null && existing.exists()) {
            existing.toLot()?.let { return it }
        }
        val data = mapOf(
            "estanque" to cleanTank,
            "fecha" to Timestamp(now),
            "creadoPor" to session.uid,
            "nombreCreador" to session.name,
            "observacion" to ""
        )
        // Sin esperar al servidor: funciona sin conexion y se sincroniza despues.
        lots.document(id).set(data).addOnFailureListener { onError(it) }
        return Lot(id, cleanTank, now, session.uid, session.name, "")
    }

    /** Editar un lote (solo encargado y administrador, segun las reglas). */
    suspend fun updateLot(lotId: String, tank: String, note: String) {
        lots.document(lotId).update(
            mapOf(
                "estanque" to tank.trim(),
                "observacion" to note.trim()
            )
        ).await()
    }

    // ------------------------------------------------------------ caja de sensores

    /** true si existe, false si no, null si no se pudo saber (por ejemplo, sin conexion). */
    suspend fun defaultDeviceExists(): Boolean? =
        runCatching { db.collection("dispositivos").document(DEFAULT_DEVICE_ID).get().await().exists() }.getOrNull()

    /** Registra la caja por defecto (solo el administrador puede, segun las reglas). */
    suspend fun registerDefaultDevice() {
        db.collection("dispositivos").document(DEFAULT_DEVICE_ID).set(
            mapOf(
                "nombre" to "Caja de sensores 1",
                "ubicacion" to "Sala de ordeña",
                "estado" to "activo"
            )
        ).await()
    }

    // ------------------------------------------------------------ mediciones

    /**
     * Guarda la lectura actual como medicion del lote. No espera al servidor (modo sin conexion);
     * si las reglas la rechazan, se avisa por onError.
     */
    fun saveMeasurement(
        reading: MilkQualityReading,
        lotId: String,
        session: UserSession,
        onSynced: (measurementId: String) -> Unit,
        onError: (Exception) -> Unit
    ): String {
        val id = UUID.randomUUID().toString()
        val assessment = reading.overallAssessment
        val data = mapOf(
            "idDispositivo" to DEFAULT_DEVICE_ID,
            "idLote" to lotId,
            "uidLechero" to session.uid,
            "nombreLechero" to session.name,
            "fechaHora" to Timestamp(Date(reading.timestamp)),
            "sensores" to mapOf(
                "temp" to reading.temperature.toDouble(),
                "ph" to reading.ph.toDouble(),
                "cond" to reading.conductivity.toDouble(),
                "turb" to reading.turbidity.toDouble()
            ),
            "resultado" to mapOf(
                "estado" to statusFor(assessment.level),
                "titulo" to assessment.title
            )
        )
        // La tarea termina cuando el servidor acepta la medicion (o la rechaza por las reglas).
        measurements.document(id).set(data)
            .addOnSuccessListener { onSynced(id) }
            .addOnFailureListener { onError(it) }
        return id
    }

    /**
     * Historial en tiempo real segun el rol (consultas Q1 a Q3 del informe):
     * - encargado o administrador: todas, o solo las de un lote;
     * - lechero: solo las propias (la regla de Firestore exige este filtro).
     */
    fun listenHistory(
        session: UserSession,
        lotId: String?,
        onChange: (List<SavedMeasurement>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        var query: Query = measurements
        if (!session.role.canViewAllHistory) {
            query = query.whereEqualTo("uidLechero", session.uid)
        }
        if (lotId != null) {
            query = query.whereEqualTo("idLote", lotId)
        }
        return query.orderBy("fechaHora", Query.Direction.DESCENDING)
            .limit(HISTORY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.mapNotNull { it.toMeasurement() })
                }
            }
    }

    // ---------------------------------------------------------------- alertas

    /**
     * Crea una alerta por cada valor fuera de rango. Se llama cuando el servidor ya acepto la
     * medicion, porque la regla exige que la medicion exista. El ID (medicion-tipo) evita duplicados.
     * Devuelve cuantas alertas se generaron.
     */
    fun createAlertsFor(
        measurementId: String,
        reading: MilkQualityReading,
        lotId: String,
        session: UserSession,
        onError: (Exception) -> Unit
    ): Int {
        val candidates = detectAlerts(reading)
        candidates.forEach { a ->
            alerts.document("$measurementId-${a.type}").set(
                mapOf(
                    "idMedicion" to measurementId,
                    "idLote" to lotId,
                    "tipo" to a.type,
                    "severidad" to a.severity.id,
                    "fecha" to Timestamp(Date(reading.timestamp)),
                    "atendida" to false,
                    "valor" to a.value,
                    "mensaje" to a.message,
                    "nombreLechero" to session.name
                )
            ).addOnFailureListener { onError(it) }
        }
        return candidates.size
    }

    /** Alertas en tiempo real: solo pendientes (consulta Q5) o todas las recientes. */
    fun listenAlerts(
        onlyPending: Boolean,
        onChange: (List<MilkAlert>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        var query: Query = alerts
        if (onlyPending) {
            query = query.whereEqualTo("atendida", false)
        }
        return query.orderBy("fecha", Query.Direction.DESCENDING)
            .limit(HISTORY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.mapNotNull { it.toAlert() })
                }
            }
    }

    /** Marcar como atendida: la regla solo permite cambiar este campo (encargado y administrador). */
    suspend fun markAlertAttended(alertId: String) {
        alerts.document(alertId).update("atendida", true).await()
    }

    // ------------------------------------------------------------- utilidades

    private fun DocumentSnapshot.toAlert(): MilkAlert? {
        val date = getTimestamp("fecha")?.toDate() ?: return null
        return MilkAlert(
            id = id,
            measurementId = getString("idMedicion").orEmpty(),
            lotId = getString("idLote").orEmpty(),
            type = getString("tipo").orEmpty(),
            severity = AlertSeverity.fromId(getString("severidad")),
            date = date,
            attended = getBoolean("atendida") == true,
            value = getDouble("valor") ?: 0.0,
            message = getString("mensaje").orEmpty(),
            milkerName = getString("nombreLechero").orEmpty()
        )
    }

    private fun DocumentSnapshot.toLot(): Lot? {
        val date = getTimestamp("fecha")?.toDate() ?: return null
        return Lot(
            id = id,
            tank = getString("estanque").orEmpty(),
            date = date,
            createdBy = getString("creadoPor").orEmpty(),
            createdByName = getString("nombreCreador").orEmpty(),
            note = getString("observacion").orEmpty()
        )
    }

    private fun DocumentSnapshot.toMeasurement(): SavedMeasurement? {
        val date = getTimestamp("fechaHora")?.toDate() ?: return null
        return SavedMeasurement(
            id = id,
            dateTime = date,
            lotId = getString("idLote").orEmpty(),
            deviceId = getString("idDispositivo").orEmpty(),
            milkerUid = getString("uidLechero").orEmpty(),
            milkerName = getString("nombreLechero").orEmpty(),
            temperature = getDouble("sensores.temp") ?: 0.0,
            ph = getDouble("sensores.ph") ?: 0.0,
            conductivity = getDouble("sensores.cond") ?: 0.0,
            turbidity = getDouble("sensores.turb") ?: 0.0,
            status = getString("resultado.estado").orEmpty(),
            title = getString("resultado.titulo").orEmpty()
        )
    }

    companion object {
        /** Semaforo del informe (RF3): verde, ambar o rojo. */
        fun statusFor(level: QualityLevel): String = when (level) {
            QualityLevel.EXCELLENT -> "verde"
            QualityLevel.ACCEPTABLE, QualityLevel.WARNING -> "ambar"
            QualityLevel.REJECTED -> "rojo"
        }

        /** ID del lote: 20261112-estanque-1 (fecha + estanque sin tildes ni espacios). */
        fun lotIdFor(tank: String, date: Date): String {
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
            val slug = Normalizer.normalize(tank.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "estanque" }
            return "$day-$slug"
        }
    }
}
