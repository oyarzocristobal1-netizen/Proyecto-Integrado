package com.example.myapplication.model

/** Que se elimino (valores que acepta la bitacora "eliminaciones" en las reglas de Firestore). */
enum class DeletionType(val id: String, val label: String) {
    USER("usuario", "cuenta de usuario"),
    DEVICE("dispositivo", "dispositivo"),
    TANK("estanque", "estanque"),
    LOT("lote", "lote"),
    MEASUREMENT("medicion", "medición"),
    ALERT("alerta", "alerta"),
    CALIBRATION("calibracion", "calibración")
}

/**
 * Reglas para eliminar de verdad (solo administrador). Son independientes de Firebase para poder probarlas.
 * Lo normal es desactivar, anular o cerrar; eliminar es el ultimo recurso y siempre queda en la bitacora.
 */
object DeletionRules {
    /** Firestore admite 500 escrituras por lote; se deja margen para la anotacion de la bitacora. */
    const val MAX_BATCH_WRITES = 400

    /** Tipos de alerta que puede generar una medicion (el ID de la alerta es medicion-tipo). */
    val ALERT_TYPES = listOf("temperatura", "ph", "conductividad", "turbidez")

    /** IDs de las alertas que puede tener una medicion, para eliminarlas junto con ella. */
    fun alertIdsFor(measurementId: String): List<String> = ALERT_TYPES.map { "$measurementId-$it" }

    /** Reparte los documentos en grupos que caben en una escritura atomica de Firestore. */
    fun <T> chunk(items: List<T>, size: Int = MAX_BATCH_WRITES): List<List<T>> {
        require(size > 0) { "El tamaño del grupo debe ser mayor que cero." }
        return items.chunked(size)
    }

    /** Descripcion para la bitacora: nunca vacia y dentro del maximo que aceptan las reglas (300). */
    fun logDescription(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ").ifEmpty { "(sin descripción)" }
        return if (clean.length <= MAX_LOG_TEXT) clean else clean.take(MAX_LOG_TEXT - 1) + "…"
    }

    /** ID del elemento para la bitacora (maximo 200 en las reglas). */
    fun logId(id: String): String = id.trim().ifEmpty { "(sin id)" }.take(MAX_LOG_ID)

    const val MAX_LOG_TEXT = 300
    const val MAX_LOG_ID = 200
}
