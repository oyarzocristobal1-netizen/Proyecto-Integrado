package com.example.myapplication.data

import com.example.myapplication.model.DeletionRules
import com.example.myapplication.model.DeletionType
import com.example.myapplication.model.UserSession
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Eliminacion real de registros (solo administrador, segun las reglas de Firestore).
 * Es el ultimo recurso: lo normal es desactivar, anular o cerrar. Cada eliminacion deja una anotacion
 * en la coleccion "eliminaciones" (quien, que y cuando) que nadie puede editar ni borrar.
 * Los registros que dependen de lo eliminado se borran con el (mediciones y alertas de un lote,
 * alertas de una medicion, calibraciones de un dispositivo), por lotes atomicos de hasta 400 escrituras.
 */
class DeletionRepository(private val db: FirebaseFirestore) {

    private val lots = db.collection("lotes")
    private val measurements = db.collection("mediciones")
    private val alerts = db.collection("alertas")
    private val devices = db.collection("dispositivos")
    private val calibrations = db.collection("calibraciones")
    private val tanks = db.collection("estanques")
    private val users = db.collection("usuarios")
    private val log = db.collection("eliminaciones")

    private val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    suspend fun deleteTank(tank: Tank, session: UserSession) {
        execute(
            refs = listOf(tanks.document(tank.id)),
            type = DeletionType.TANK,
            id = tank.id,
            description = "Estanque \"${tank.name}\"",
            session = session
        )
    }

    suspend fun deleteAlert(alert: MilkAlert, session: UserSession) {
        execute(
            refs = listOf(alerts.document(alert.id)),
            type = DeletionType.ALERT,
            id = alert.id,
            description = "Alerta \"${alert.message}\" del ${dateTime.format(alert.date)}",
            session = session
        )
    }

    /** Elimina la medicion y sus alertas (las que existan; borrar una que no existe no falla). */
    suspend fun deleteMeasurement(measurement: SavedMeasurement, lotName: String, session: UserSession) {
        val refs = DeletionRules.alertIdsFor(measurement.id).map { alerts.document(it) } +
            measurements.document(measurement.id)
        execute(
            refs = refs,
            type = DeletionType.MEASUREMENT,
            id = measurement.id,
            description = "Medición del ${dateTime.format(measurement.dateTime)} en $lotName " +
                "(${measurement.milkerName.ifBlank { "sin nombre" }}${if (measurement.isSimulated) ", simulada" else ""})",
            session = session
        )
    }

    /** Elimina el lote con todas sus mediciones y alertas. Devuelve cuantas mediciones se eliminaron. */
    suspend fun deleteLot(lot: Lot, session: UserSession): Int {
        val lotMeasurements = measurements.whereEqualTo("idLote", lot.id).get(Source.SERVER).await().documents
        val lotAlerts = alerts.whereEqualTo("idLote", lot.id).get(Source.SERVER).await().documents
        val refs = lotAlerts.map { it.reference } + lotMeasurements.map { it.reference } + lots.document(lot.id)
        execute(
            refs = refs,
            type = DeletionType.LOT,
            id = lot.id,
            description = "Lote \"${lot.tank}\" del ${dateTime.format(lot.date)} con ${lotMeasurements.size} medición(es) " +
                "y ${lotAlerts.size} alerta(s)",
            session = session
        )
        return lotMeasurements.size
    }

    /** Elimina el dispositivo con sus calibraciones. Las mediciones antiguas conservan su ID de dispositivo. */
    suspend fun deleteDevice(device: Device, session: UserSession): Int {
        val deviceCalibrations = calibrations.whereEqualTo("idDispositivo", device.id).get(Source.SERVER).await().documents
        val refs = deviceCalibrations.map { it.reference } + devices.document(device.id)
        execute(
            refs = refs,
            type = DeletionType.DEVICE,
            id = device.id,
            description = "Dispositivo \"${device.name}\" con ${deviceCalibrations.size} calibración(es)",
            session = session
        )
        return deviceCalibrations.size
    }

    suspend fun deleteCalibration(calibration: Calibration, deviceName: String, session: UserSession) {
        execute(
            refs = listOf(calibrations.document(calibration.id)),
            type = DeletionType.CALIBRATION,
            id = calibration.id,
            description = "Calibración de $deviceName (${calibration.parameter.label}) del ${dateTime.format(calibration.date)}",
            session = session
        )
    }

    /**
     * Elimina el perfil de la cuenta: pierde el acceso a la app. La cuenta de acceso en Firebase
     * Authentication no se puede borrar desde la app (hay que hacerlo en la consola).
     */
    suspend fun deleteUserProfile(user: ManagedUser, session: UserSession) {
        execute(
            refs = listOf(users.document(user.uid)),
            type = DeletionType.USER,
            id = user.uid,
            description = "Cuenta de ${user.name.ifBlank { user.email }} (${user.role?.label ?: "sin rol"})",
            session = session
        )
    }

    /**
     * Borra los documentos en grupos atomicos. La anotacion de la bitacora va en el ultimo grupo, junto
     * con el documento principal (que es el ultimo de la lista): si un grupo anterior falla, no se pierde
     * el elemento principal y se puede reintentar.
     */
    private suspend fun execute(
        refs: List<DocumentReference>,
        type: DeletionType,
        id: String,
        description: String,
        session: UserSession
    ) {
        val groups = DeletionRules.chunk(refs)
        groups.forEachIndexed { index, group ->
            val batch = db.batch()
            group.forEach { batch.delete(it) }
            if (index == groups.lastIndex) {
                batch.set(
                    log.document(),
                    mapOf(
                        "tipo" to type.id,
                        "idElemento" to DeletionRules.logId(id),
                        "descripcion" to DeletionRules.logDescription(description),
                        "eliminadoPor" to session.name,
                        "fecha" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
    }
}
