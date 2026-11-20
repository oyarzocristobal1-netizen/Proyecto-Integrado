package com.example.myapplication.model

/**
 * Validaciones de los formularios de administracion (estanques, anular mediciones, notas de
 * alertas, dispositivos y calibraciones). Son las mismas cotas que exigen las reglas de Firestore
 * (firebase/firestore.rules): la app avisa antes y el servidor las hace cumplir.
 * Todas devuelven un mensaje si hay un problema, o null si el dato es valido.
 */
object CrudRules {
    const val ANNUL_REASON_MIN = 3
    const val ANNUL_REASON_MAX = 200
    const val ALERT_NOTE_MAX = 300
    const val DEVICE_NAME_MAX = 60
    const val DEVICE_LOCATION_MAX = 60
    const val CALIBRATION_NOTE_MAX = 200

    fun validateAnnulReason(reason: String): String? {
        val clean = reason.trim()
        return when {
            clean.length < ANNUL_REASON_MIN -> "Escribe el motivo (al menos $ANNUL_REASON_MIN caracteres)."
            clean.length > ANNUL_REASON_MAX -> "El motivo no puede superar $ANNUL_REASON_MAX caracteres."
            else -> null
        }
    }

    /** La nota al atender una alerta es opcional. */
    fun validateAlertNote(note: String): String? =
        if (note.trim().length > ALERT_NOTE_MAX) "La nota no puede superar $ALERT_NOTE_MAX caracteres." else null

    fun validateDeviceName(name: String): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Escribe el nombre del dispositivo (por ejemplo, Caja de sensores 2)."
            clean.length > DEVICE_NAME_MAX -> "El nombre no puede superar $DEVICE_NAME_MAX caracteres."
            RecordRules.slug(clean, "").isEmpty() -> "El nombre debe llevar al menos una letra o un número."
            else -> null
        }
    }

    fun validateDeviceLocation(location: String): String? =
        if (location.trim().length > DEVICE_LOCATION_MAX) "La ubicación no puede superar $DEVICE_LOCATION_MAX caracteres." else null

    fun validateCalibrationNote(note: String): String? =
        if (note.trim().length > CALIBRATION_NOTE_MAX) "La nota no puede superar $CALIBRATION_NOTE_MAX caracteres." else null

    /** ID del dispositivo a partir de su nombre: "Caja de sensores 2" -> "caja-de-sensores-2". */
    fun deviceIdFor(name: String): String = RecordRules.slug(name, "dispositivo")
}

/** Parametro que se calibro (valores que aceptan las reglas de Firestore). */
enum class CalibrationParameter(val id: String, val label: String) {
    ALL("todos", "Todos los sensores"),
    TEMPERATURE("temperatura", "Temperatura"),
    PH("ph", "pH"),
    CONDUCTIVITY("conductividad", "Conductividad"),
    TURBIDITY("turbidez", "Turbidez");

    companion object {
        fun fromId(id: String?): CalibrationParameter = entries.firstOrNull { it.id == id } ?: ALL
    }
}
