package com.example.myapplication.model

/**
 * Rangos de calidad vigentes (Firestore: configuracion/rangos). Solo el administrador los cambia.
 * Los valores por defecto son los de referencia del informe; se validan en las pruebas de terreno.
 */
data class QualityRanges(
    /** Temperatura maxima de la leche refrigerada (°C). */
    val tempMax: Float = 4.0f,
    val phMin: Float = 6.6f,
    val phMax: Float = 6.8f,
    /** Conductividad electrica (mS/cm). */
    val condMin: Float = 4.0f,
    val condMax: Float = 5.5f,
    /** Turbidez minima (%): por debajo, posible aguado. */
    val turbMin: Float = 50.0f
) {
    /** Devuelve un mensaje si los valores no tienen sentido, o null si estan bien. */
    fun validate(): String? = when {
        tempMax < 0f || tempMax > 20f -> "La temperatura máxima debe estar entre 0 y 20 °C."
        phMin < 0f || phMax > 14f -> "El pH debe estar entre 0 y 14."
        phMin >= phMax -> "El pH mínimo debe ser menor que el pH máximo."
        condMin < 0f -> "La conductividad mínima no puede ser negativa."
        condMin >= condMax -> "La conductividad mínima debe ser menor que la máxima."
        condMax > 20f -> "La conductividad máxima no puede superar 20 mS/cm."
        turbMin < 0f || turbMin > 100f -> "La turbidez mínima debe estar entre 0 y 100 %."
        else -> null
    }
}

/**
 * Rangos en uso en toda la app. Se actualiza cuando cambia configuracion/rangos en Firestore;
 * si no hay conexion o el documento no existe, se usan los valores por defecto.
 */
object QualityRangesHolder {
    @Volatile
    var current: QualityRanges = QualityRanges()
}

/** Temperatura sobre la cual la leche se considera demasiado caliente (no configurable). */
const val TEMP_AMBIENT_MAX = 25.0f
