package com.example.myapplication.data

import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import com.example.myapplication.model.RecordRules
import com.example.myapplication.model.UserSession
import com.example.myapplication.model.detectAlerts
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Date
import java.util.UUID

/** Mediciones por pagina del historial (controla el consumo de lecturas, informe 3.10). */
const val HISTORY_PAGE_SIZE = 50L

/** Maximo de mediciones por reporte. */
const val REPORT_LIMIT = 2000L

/** Maximo de mediciones que lee el panel principal (ultimos 7 dias). */
const val DASHBOARD_LIMIT = 500L

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
    val title: String,
    /** Simulada (modo DEMO) o tomada por el sensor. */
    val source: ReadingSource,
    /** true mientras la medicion esta solo en el telefono y aun no llega al servidor. */
    val pendingSync: Boolean
) {
    val isSimulated: Boolean get() = source == ReadingSource.SIMULATED
}

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
    val milkerName: String,
    val source: ReadingSource
) {
    val isSimulated: Boolean get() = source == ReadingSource.SIMULATED
}

/** Resultado de registrar una medicion. */
data class RecordResult(val measurementId: String, val alertCount: Int)

/**
 * Lotes, mediciones, alertas y rangos en Cloud Firestore.
 * Firestore guarda una copia local en el telefono (cache persistente, ver AppContainer):
 * sin conexion las escrituras quedan en cola y se suben solas al volver la red.
 */
class RecordsRepository(
    private val db: FirebaseFirestore
) {
    private val lots = db.collection("lotes")
    private val measurements = db.collection("mediciones")
    private val alerts = db.collection("alertas")
    private val devices = db.collection("dispositivos")
    private val rangesDoc = db.collection("configuracion").document("rangos")

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
     * Devuelve el lote de hoy para ese estanque, o lo crea (el nombre ya viene validado).
     * El ID es fecha + estanque, asi no se duplica aunque dos personas lo creen a la vez.
     */
    suspend fun getOrCreateTodayLot(
        tank: String,
        session: UserSession,
        onError: (Exception) -> Unit
    ): Lot {
        val cleanTank = tank.trim()
        val now = Date()
        val id = RecordRules.lotIdFor(cleanTank, now)
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

    // ------------------------------------------------------------ dispositivos

    /** true si existe, false si no, null si no se pudo saber (por ejemplo, sin conexion). */
    suspend fun deviceExists(id: String): Boolean? =
        runCatching { devices.document(id).get().await().exists() }.getOrNull()

    /** Registra la caja real o el simulador (solo el administrador, segun las reglas). */
    suspend fun registerDevice(id: String) {
        val data = if (id == RecordRules.SIMULATOR_DEVICE_ID) {
            mapOf("nombre" to "Simulador (modo DEMO)", "ubicacion" to "Aplicación", "estado" to "activo")
        } else {
            mapOf("nombre" to "Caja de sensores 1", "ubicacion" to "Sala de ordeña", "estado" to "activo")
        }
        devices.document(id).set(data).await()
    }

    // ------------------------------------------------------------ mediciones

    /**
     * Guarda la lectura (ya validada) como medicion del lote, junto con sus alertas, en una sola
     * escritura atomica: o se guardan todas o ninguna. No espera al servidor, asi funciona sin
     * conexion; onSynced se llama cuando el servidor la acepta y onError si las reglas la rechazan.
     */
    fun recordMeasurement(
        reading: MilkQualityReading,
        lotId: String,
        session: UserSession,
        ranges: QualityRanges,
        onSynced: () -> Unit,
        onError: (Exception) -> Unit
    ): RecordResult {
        val id = UUID.randomUUID().toString()
        val assessment = reading.overallAssessment
        val measuredAt = Timestamp(Date(reading.timestamp))
        val batch = db.batch()
        batch.set(
            measurements.document(id),
            mapOf(
                "idDispositivo" to RecordRules.deviceIdFor(reading.source),
                "idLote" to lotId,
                "uidLechero" to session.uid,
                "nombreLechero" to session.name,
                "fechaHora" to measuredAt,
                // Hora del servidor al recibirla: no depende del reloj del telefono.
                "registradoEn" to FieldValue.serverTimestamp(),
                "origen" to reading.source.id,
                "sensores" to mapOf(
                    "temp" to reading.temperature.clean(),
                    "ph" to reading.ph.clean(),
                    "cond" to reading.conductivity.clean(),
                    "turb" to reading.turbidity.clean()
                ),
                "resultado" to mapOf(
                    "estado" to RecordRules.statusFor(assessment.level),
                    "titulo" to assessment.title
                )
            )
        )
        val candidates = detectAlerts(reading, ranges)
        candidates.forEach { a ->
            // ID medicion-tipo: no se duplica aunque la escritura se reintente.
            batch.set(
                alerts.document("$id-${a.type}"),
                mapOf(
                    "idMedicion" to id,
                    "idLote" to lotId,
                    "tipo" to a.type,
                    "severidad" to a.severity.id,
                    "fecha" to measuredAt,
                    "atendida" to false,
                    "valor" to a.value,
                    "mensaje" to a.message,
                    "nombreLechero" to session.name,
                    "origen" to reading.source.id
                )
            )
        }
        batch.commit()
            .addOnSuccessListener { onSynced() }
            .addOnFailureListener { onError(it) }
        return RecordResult(id, candidates.size)
    }

    /**
     * Historial en tiempo real segun el rol (consultas Q1 a Q3 del informe), por paginas:
     * - encargado o administrador: todas, o solo las de un lote;
     * - lechero: solo las propias (la regla de Firestore exige este filtro).
     * Incluye cambios de metadatos para saber cuando una medicion termina de sincronizarse.
     */
    fun listenHistory(
        session: UserSession,
        lotId: String?,
        limit: Long,
        onChange: (List<SavedMeasurement>, hasMore: Boolean) -> Unit,
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
            .limit(limit)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.mapNotNull { it.toMeasurement() }, snapshot.size() >= limit)
                }
            }
    }

    /** Mediciones desde una fecha, para el panel principal (lechero: solo las propias). */
    fun listenSince(
        session: UserSession,
        since: Date,
        onChange: (List<SavedMeasurement>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        var query: Query = measurements
        if (!session.role.canViewAllHistory) {
            query = query.whereEqualTo("uidLechero", session.uid)
        }
        return query.whereGreaterThanOrEqualTo("fechaHora", Timestamp(since))
            .orderBy("fechaHora", Query.Direction.DESCENDING)
            .limit(DASHBOARD_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.mapNotNull { it.toMeasurement() })
                }
            }
    }

    // ---------------------------------------------------------------- alertas

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
            .limit(HISTORY_PAGE_SIZE)
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

    // ---------------------------------------------------------------- reportes

    /**
     * Mediciones de un periodo (consulta Q4 del informe), opcionalmente de un lote,
     * de la mas nueva a la mas antigua. Solo encargado y administrador (lo exigen las reglas).
     */
    suspend fun fetchForReport(from: Date, to: Date, lotId: String?): List<SavedMeasurement> {
        var query: Query = measurements
        if (lotId != null) {
            query = query.whereEqualTo("idLote", lotId)
        }
        return query.whereGreaterThanOrEqualTo("fechaHora", Timestamp(from))
            .whereLessThanOrEqualTo("fechaHora", Timestamp(to))
            .orderBy("fechaHora", Query.Direction.DESCENDING)
            .limit(REPORT_LIMIT)
            .get().await()
            .documents.mapNotNull { it.toMeasurement() }
    }

    // ------------------------------------------------------------------ rangos

    /** Rangos vigentes en tiempo real; si el documento no existe, se usan los valores por defecto. */
    fun listenRanges(
        onChange: (QualityRanges) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        rangesDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
            } else {
                val d = QualityRanges()
                if (snapshot == null || !snapshot.exists()) {
                    onChange(d)
                } else {
                    fun num(field: String, default: Float) = snapshot.getDouble(field)?.toFloat() ?: default
                    val ranges = QualityRanges(
                        tempMax = num("tempMax", d.tempMax),
                        phMin = num("phMin", d.phMin),
                        phMax = num("phMax", d.phMax),
                        condMin = num("condMin", d.condMin),
                        condMax = num("condMax", d.condMax),
                        turbMin = num("turbMin", d.turbMin)
                    )
                    // Un documento editado a mano con valores incoherentes no debe romper la evaluacion.
                    onChange(if (ranges.validate() == null) ranges else d)
                }
            }
        }

    /** Guarda los rangos (solo el administrador, segun las reglas). Conserva otros campos, como grasaMin. */
    suspend fun saveRanges(ranges: QualityRanges) {
        rangesDoc.set(
            mapOf(
                "tempMax" to ranges.tempMax.clean(),
                "phMin" to ranges.phMin.clean(),
                "phMax" to ranges.phMax.clean(),
                "condMin" to ranges.condMin.clean(),
                "condMax" to ranges.condMax.clean(),
                "turbMin" to ranges.turbMin.clean()
            ),
            SetOptions.merge()
        ).await()
    }

    // ------------------------------------------------------------- conversion

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
            milkerName = getString("nombreLechero").orEmpty(),
            source = ReadingSource.fromId(getString("origen"))
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

    /** Una medicion sin alguno de sus datos no se muestra (nunca se rellena con ceros). */
    private fun DocumentSnapshot.toMeasurement(): SavedMeasurement? {
        val date = getTimestamp("fechaHora")?.toDate() ?: return null
        val temp = getDouble("sensores.temp") ?: return null
        val ph = getDouble("sensores.ph") ?: return null
        val cond = getDouble("sensores.cond") ?: return null
        val turb = getDouble("sensores.turb") ?: return null
        return SavedMeasurement(
            id = id,
            dateTime = date,
            lotId = getString("idLote").orEmpty(),
            deviceId = getString("idDispositivo").orEmpty(),
            milkerUid = getString("uidLechero").orEmpty(),
            milkerName = getString("nombreLechero").orEmpty(),
            temperature = temp,
            ph = ph,
            conductivity = cond,
            turbidity = turb,
            status = getString("resultado.estado").orEmpty(),
            title = getString("resultado.titulo").orEmpty(),
            source = ReadingSource.fromId(getString("origen")),
            pendingSync = metadata.hasPendingWrites()
        )
    }

    /** Float a Double sin ruido de redondeo (6.7f -> 6.7 y no 6.699999809). */
    private fun Float.clean(): Double = toString().toDouble()
}
