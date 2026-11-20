package com.example.myapplication.data

import com.example.myapplication.model.CalibrationParameter
import com.example.myapplication.model.UserSession
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

/** Dispositivo (coleccion "dispositivos"): la caja de sensores real o el simulador. */
data class Device(
    val id: String,
    val name: String,
    val location: String,
    val active: Boolean,
    val lastCalibration: Date?
)

/** Calibracion registrada de un dispositivo (coleccion "calibraciones"). */
data class Calibration(
    val id: String,
    val deviceId: String,
    val date: Date,
    val doneBy: String,
    val parameter: CalibrationParameter,
    val note: String
)

/** Resultado de crear un dispositivo. */
enum class DeviceResult { CREATED, ALREADY_EXISTS }

/**
 * Dispositivos y calibraciones (solo administrador, segun las reglas de Firestore).
 * Los dispositivos se desactivan, no se borran, para que las mediciones antiguas sigan
 * apuntando a un dispositivo que existe.
 */
class DevicesRepository(
    db: FirebaseFirestore
) {
    private val devices = db.collection("dispositivos")
    private val calibrations = db.collection("calibraciones")
    private val firestore = db

    fun listenDevices(
        onChange: (List<Device>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        devices.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
            } else if (snapshot != null) {
                onChange(
                    snapshot.documents.map { d ->
                        Device(
                            id = d.id,
                            name = d.getString("nombre").orEmpty().ifBlank { d.id },
                            location = d.getString("ubicacion").orEmpty(),
                            active = d.getString("estado") != "inactivo",
                            lastCalibration = d.getTimestamp("ultimaCalibracion")?.toDate()
                        )
                    }.sortedWith(compareByDescending<Device> { it.active }.thenBy { it.name.lowercase() })
                )
            }
        }

    /** Crea un dispositivo con el ID que sale de su nombre; no pisa uno que ya existe. */
    suspend fun createDevice(id: String, name: String, location: String): DeviceResult {
        val ref = devices.document(id)
        if (ref.get().await().exists()) return DeviceResult.ALREADY_EXISTS
        ref.set(
            mapOf("nombre" to name.trim(), "ubicacion" to location.trim(), "estado" to "activo")
        ).await()
        return DeviceResult.CREATED
    }

    /**
     * Edita nombre, ubicacion y estado. Conserva la fecha de ultima calibracion
     * (la regla de Firestore valida el documento completo).
     */
    suspend fun updateDevice(device: Device, name: String, location: String, active: Boolean) {
        val data = mutableMapOf<String, Any>(
            "nombre" to name.trim(),
            "ubicacion" to location.trim(),
            "estado" to if (active) "activo" else "inactivo"
        )
        device.lastCalibration?.let { data["ultimaCalibracion"] = com.google.firebase.Timestamp(it) }
        devices.document(device.id).set(data).await()
    }

    /** Calibraciones de un dispositivo, de la mas reciente a la mas antigua. */
    fun listenCalibrations(
        deviceId: String,
        onChange: (List<Calibration>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        calibrations.whereEqualTo("idDispositivo", deviceId)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(
                        snapshot.documents.mapNotNull { d ->
                            val date = d.getTimestamp("fecha")?.toDate() ?: return@mapNotNull null
                            Calibration(
                                id = d.id,
                                deviceId = d.getString("idDispositivo").orEmpty(),
                                date = date,
                                doneBy = d.getString("realizadaPor").orEmpty(),
                                parameter = CalibrationParameter.fromId(d.getString("parametro")),
                                note = d.getString("nota").orEmpty()
                            )
                        }
                    )
                }
            }

    /**
     * Registra una calibracion y actualiza la fecha de ultima calibracion del dispositivo,
     * en una sola escritura atomica. La fecha es la del servidor.
     */
    suspend fun addCalibration(
        device: Device,
        parameter: CalibrationParameter,
        note: String,
        session: UserSession
    ) {
        val batch = firestore.batch()
        val data = mutableMapOf<String, Any>(
            "idDispositivo" to device.id,
            "fecha" to FieldValue.serverTimestamp(),
            "realizadaPor" to session.name,
            "parametro" to parameter.id
        )
        if (note.isNotBlank()) data["nota"] = note.trim()
        batch.set(calibrations.document(), data)
        batch.update(devices.document(device.id), "ultimaCalibracion", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    /** Corrige la nota de una calibracion (lo unico que permiten las reglas). */
    suspend fun updateCalibrationNote(calibrationId: String, note: String) {
        calibrations.document(calibrationId).update("nota", note.trim()).await()
    }
}
