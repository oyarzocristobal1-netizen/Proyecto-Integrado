package com.example.myapplication.data

import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import com.example.myapplication.model.RecordRules
import com.example.myapplication.model.ReviewRules
import com.example.myapplication.model.SaleDecision
import com.example.myapplication.model.DeletionRules
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
import com.google.firebase.firestore.Source
import java.util.Date
import java.util.UUID

/** Mediciones por pagina del historial (controla el consumo de lecturas, informe 3.10). */
const val HISTORY_PAGE_SIZE = 50L

/** Maximo de mediciones por reporte. */
const val REPORT_LIMIT = 2000L

/** Maximo de mediciones que lee el panel principal (ultimos 7 dias). */
const val DASHBOARD_LIMIT = 500L

/** Maximo de documentos por consulta de los avisos en segundo plano (cuida el consumo de lecturas). */
const val NOTICE_LIMIT = 200L

/** Lote: la leche de un estanque en un dia (informe, Tabla 20: un lote por estanque y dia). */
data class Lot(
    val id: String,
    val tank: String,
    val date: Date,
    val createdBy: String,
    val createdByName: String,
    val note: String,
    /** Lote cerrado: no admite mas mediciones (se puede reabrir). */
    val closed: Boolean = false,
    /** Quien lo cerro o reabrio por ultima vez (vacio si nunca se cambio). */
    val stateByName: String = "",
    val stateAt: Date? = null,
    /** Decision del encargado: apto o no apto para venta (null si aun no se decide). */
    val sale: SaleDecision? = null,
    val saleNote: String = "",
    val saleByName: String = "",
    val saleAt: Date? = null
)

/** Estanque del catalogo (coleccion "estanques"). Se desactiva, no se borra. */
data class Tank(
    val id: String,
    val name: String,
    val active: Boolean
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
    val pendingSync: Boolean,
    /** Anulada por el encargado: se conserva en el historial pero no cuenta en analisis ni reportes. */
    val annulled: Boolean = false,
    val annulReason: String = "",
    val annulledByName: String = "",
    val annulledAt: Date? = null,
    /** Nota que escribio el trabajador al registrar (vacia si no escribio nada). */
    val note: String = "",
    /** El trabajador pidio que el encargado revise esta medicion. */
    val reviewRequested: Boolean = false,
    val reviewDone: Boolean = false,
    val reviewByName: String = "",
    val reviewAt: Date? = null,
    val reviewNote: String = ""
) {
    val isSimulated: Boolean get() = source == ReadingSource.SIMULATED
}

/** Alerta guardada (coleccion "alertas"). Solo se puede atender y escribir una nota. */
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
    val source: ReadingSource,
    /** Nota de quien la atendio (que se hizo). */
    val note: String = "",
    val attendedByName: String = "",
    val attendedAt: Date? = null
) {
    val isSimulated: Boolean get() = source == ReadingSource.SIMULATED
}

/** Resultado de crear un estanque. */
enum class TankResult { CREATED, REACTIVATED, ALREADY_EXISTS }

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
    private val tanks = db.collection("estanques")
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

    /**
     * Cierra o reabre un lote (solo encargado y administrador). Queda quien lo hizo y cuando.
     * Un lote cerrado no admite mediciones nuevas: lo exigen las reglas de Firestore.
     */
    suspend fun setLotClosed(lotId: String, closed: Boolean, session: UserSession) {
        lots.document(lotId).update(
            mapOf(
                "cerrado" to closed,
                "estadoPor" to session.name,
                "estadoEn" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * El encargado (o el administrador) decide si el lote es apto o no apto para venta, con una nota opcional.
     * Queda quien lo decidio y cuando. Se puede volver a decidir.
     */
    suspend fun setLotSale(lotId: String, decision: SaleDecision, note: String, session: UserSession) {
        val data = mutableMapOf<String, Any>(
            "venta" to decision.id,
            "ventaPor" to session.name,
            "ventaEn" to FieldValue.serverTimestamp()
        )
        // Con nota vacia se borra la nota anterior (si la hubiera) para que no quede una explicacion vieja.
        data["ventaNota"] = if (note.isBlank()) FieldValue.delete() else note.trim()
        lots.document(lotId).update(data).await()
    }

    // --------------------------------------------------------------- estanques

    /** Catalogo de estanques en tiempo real (activos primero, por nombre). */
    fun listenTanks(
        onChange: (List<Tank>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        tanks.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
            } else if (snapshot != null) {
                onChange(
                    snapshot.documents.mapNotNull { it.toTank() }
                        .sortedWith(compareByDescending<Tank> { it.active }.thenBy { it.name.lowercase() })
                )
            }
        }

    /**
     * Crea un estanque (el nombre ya viene validado). El ID sale del nombre, asi no se duplica;
     * si ya existe se rechaza, salvo que este desactivado (entonces se reactiva).
     */
    suspend fun createTank(name: String, session: UserSession): TankResult {
        val clean = name.trim()
        val id = RecordRules.slug(clean, "estanque")
        val ref = tanks.document(id)
        val existing = ref.get().await()
        if (existing.exists()) {
            return if (existing.getBoolean("activo") == false) {
                ref.update(mapOf("nombre" to clean, "activo" to true)).await()
                TankResult.REACTIVATED
            } else {
                TankResult.ALREADY_EXISTS
            }
        }
        ref.set(
            mapOf("nombre" to clean, "activo" to true, "creadoPor" to session.uid)
        ).await()
        return TankResult.CREATED
    }

    /**
     * Cambia el nombre de un estanque o lo activa/desactiva. Los lotes ya creados conservan el
     * nombre con el que se crearon.
     */
    suspend fun updateTank(id: String, name: String, active: Boolean) {
        tanks.document(id).update(mapOf("nombre" to name.trim(), "activo" to active)).await()
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
        onError: (Exception) -> Unit,
        note: String = "",
        requestReview: Boolean = false
    ): RecordResult {
        val id = UUID.randomUUID().toString()
        val assessment = reading.overallAssessment
        val measuredAt = Timestamp(Date(reading.timestamp))
        val batch = db.batch()
        val cleanNote = ReviewRules.clean(note)
        // Campos opcionales: solo se guardan si el trabajador escribio una nota o pidio revision.
        val extras = buildMap<String, Any> {
            if (cleanNote.isNotEmpty()) put("nota", cleanNote)
            if (requestReview) put("revision", true)
        }
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
            ) + extras
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

    /**
     * Mediciones desde una fecha, para el panel principal (lechero: solo las propias).
     * Incluye cambios de metadatos para mostrar cuantas faltan por sincronizar.
     */
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.mapNotNull { it.toMeasurement() })
                }
            }
    }

    /**
     * Anula una medicion (solo encargado y administrador): no se borra, queda marcada con el motivo,
     * quien la anulo y cuando. Asi se conserva la trazabilidad ("nadie borra").
     */
    suspend fun annulMeasurement(measurementId: String, reason: String, session: UserSession) {
        measurements.document(measurementId).update(
            mapOf(
                "anulada" to true,
                "motivoAnulacion" to reason.trim(),
                "anuladaPor" to session.name,
                "anuladaEn" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** El encargado marca como revisada una medicion en la que se pidio revision (con nota opcional). */
    suspend fun resolveReview(measurementId: String, note: String, session: UserSession) {
        val data = mutableMapOf<String, Any>(
            "revisionAtendida" to true,
            "revisionPor" to session.name,
            "revisionEn" to FieldValue.serverTimestamp()
        )
        if (note.isNotBlank()) data["revisionNota"] = note.trim()
        measurements.document(measurementId).update(data).await()
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

    /**
     * Marcar como atendida, con una nota opcional de lo que se hizo (encargado y administrador).
     * Queda quien la atendio y cuando.
     */
    suspend fun markAlertAttended(alertId: String, note: String, session: UserSession) {
        val data = mutableMapOf<String, Any>(
            "atendida" to true,
            "atendidaPor" to session.name,
            "atendidaEn" to FieldValue.serverTimestamp()
        )
        if (note.isNotBlank()) data["nota"] = note.trim()
        alerts.document(alertId).update(data).await()
    }

    /** Marca varias alertas como atendidas con la misma nota (una sola escritura atomica por grupo). */
    suspend fun markAlertsAttended(alertIds: List<String>, note: String, session: UserSession) {
        DeletionRules.chunk(alertIds).forEach { group ->
            val batch = db.batch()
            group.forEach { id ->
                val data = mutableMapOf<String, Any>(
                    "atendida" to true,
                    "atendidaPor" to session.name,
                    "atendidaEn" to FieldValue.serverTimestamp()
                )
                if (note.isNotBlank()) data["nota"] = note.trim()
                batch.update(alerts.document(id), data)
            }
            batch.commit().await()
        }
    }

    /** Corrige la nota de una alerta ya atendida. */
    suspend fun updateAlertNote(alertId: String, note: String) {
        alerts.document(alertId).update("nota", note.trim()).await()
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
            // Solo del servidor: un reporte con la copia local podria quedar incompleto.
            .get(Source.SERVER).await()
            // Las anuladas no cuentan en los reportes (siguen guardadas y visibles en el historial).
            .documents.mapNotNull { it.toMeasurement() }.filterNot { it.annulled }
    }

    // ----------------------------------------------- avisos en segundo plano

    /*
     * Lecturas puntuales (no en tiempo real) para los avisos del telefono (RoleNoticesWorker).
     * Usan solo consultas que ya existen (mismos indices) y piden pocos documentos.
     */

    /** Alertas pendientes (las mismas del tablero de alertas). */
    suspend fun fetchPendingAlerts(): List<MilkAlert> =
        alerts.whereEqualTo("atendida", false)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(HISTORY_PAGE_SIZE)
            .get().await()
            .documents.mapNotNull { it.toAlert() }

    /**
     * Mediciones donde se pidio revision. Encargado y administrador ven todas; el lechero solo las suyas
     * (la regla de Firestore exige el filtro por su uid).
     */
    suspend fun fetchReviewRequests(onlyOfUid: String?): List<SavedMeasurement> {
        var query: Query = measurements
        if (onlyOfUid != null) query = query.whereEqualTo("uidLechero", onlyOfUid)
        return query.whereEqualTo("revision", true)
            .limit(NOTICE_LIMIT)
            .get().await()
            .documents.mapNotNull { it.toMeasurement() }
    }

    /** Mediciones desde [since] (todas, para encargado y administrador), de la mas nueva a la mas antigua. */
    suspend fun fetchMeasurementsSince(since: Date): List<SavedMeasurement> =
        measurements.whereGreaterThanOrEqualTo("fechaHora", Timestamp(since))
            .orderBy("fechaHora", Query.Direction.DESCENDING)
            .limit(NOTICE_LIMIT)
            .get().await()
            .documents.mapNotNull { it.toMeasurement() }

    /** Lotes creados desde [since]. */
    suspend fun fetchLotsSince(since: Date): List<Lot> =
        lots.whereGreaterThanOrEqualTo("fecha", Timestamp(since))
            .limit(NOTICE_LIMIT)
            .get().await()
            .documents.mapNotNull { it.toLot() }

    /** Rangos vigentes, una sola lectura (por defecto si no existe o esta mal editado). */
    suspend fun fetchRanges(): QualityRanges {
        val d = QualityRanges()
        val snapshot = rangesDoc.get().await()
        if (!snapshot.exists()) return d
        fun num(field: String, default: Float) = snapshot.getDouble(field)?.toFloat() ?: default
        val ranges = QualityRanges(
            tempMax = num("tempMax", d.tempMax),
            phMin = num("phMin", d.phMin),
            phMax = num("phMax", d.phMax),
            condMin = num("condMin", d.condMin),
            condMax = num("condMax", d.condMax),
            turbMin = num("turbMin", d.turbMin)
        )
        return if (ranges.validate() == null) ranges else d
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
            source = ReadingSource.fromId(getString("origen")),
            note = getString("nota").orEmpty(),
            attendedByName = getString("atendidaPor").orEmpty(),
            attendedAt = getTimestamp("atendidaEn")?.toDate()
        )
    }

    private fun DocumentSnapshot.toTank(): Tank? {
        val name = getString("nombre")?.takeIf { it.isNotBlank() } ?: return null
        return Tank(id = id, name = name, active = getBoolean("activo") != false)
    }

    private fun DocumentSnapshot.toLot(): Lot? {
        val date = getTimestamp("fecha")?.toDate() ?: return null
        return Lot(
            id = id,
            tank = getString("estanque").orEmpty(),
            date = date,
            createdBy = getString("creadoPor").orEmpty(),
            createdByName = getString("nombreCreador").orEmpty(),
            note = getString("observacion").orEmpty(),
            closed = getBoolean("cerrado") == true,
            stateByName = getString("estadoPor").orEmpty(),
            stateAt = getTimestamp("estadoEn")?.toDate(),
            sale = SaleDecision.fromId(getString("venta")),
            saleNote = getString("ventaNota").orEmpty(),
            saleByName = getString("ventaPor").orEmpty(),
            saleAt = getTimestamp("ventaEn")?.toDate()
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
            pendingSync = metadata.hasPendingWrites(),
            annulled = getBoolean("anulada") == true,
            annulReason = getString("motivoAnulacion").orEmpty(),
            annulledByName = getString("anuladaPor").orEmpty(),
            annulledAt = getTimestamp("anuladaEn")?.toDate(),
            note = getString("nota").orEmpty(),
            reviewRequested = getBoolean("revision") == true,
            reviewDone = getBoolean("revisionAtendida") == true,
            reviewByName = getString("revisionPor").orEmpty(),
            reviewAt = getTimestamp("revisionEn")?.toDate(),
            reviewNote = getString("revisionNota").orEmpty()
        )
    }

    /** Float a Double sin ruido de redondeo (6.7f -> 6.7 y no 6.699999809). */
    private fun Float.clean(): Double = toString().toDouble()
}
