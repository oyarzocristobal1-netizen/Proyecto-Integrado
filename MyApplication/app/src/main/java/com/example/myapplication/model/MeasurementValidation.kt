package com.example.myapplication.model

/**
 * Limites fisicos de cada sensor. Fuera de ellos el valor no puede venir de leche real
 * (sensor desconectado, trama corrupta o error de calibracion) y la lectura se rechaza.
 * No son los rangos de calidad (QualityRanges): una leche puede estar fuera de rango de calidad
 * y aun asi ser una lectura valida.
 */
object SensorLimits {
    val TEMPERATURE = -5.0f..80.0f      // °C
    val PH = 0.0f..14.0f                // escala de pH
    val CONDUCTIVITY = 0.0f..20.0f      // mS/cm
    val TURBIDITY = 0.0f..100.0f        // %
}

/** Resultado de validar una lectura antes de registrarla. */
data class ValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
    val firstError: String? get() = errors.firstOrNull()

    companion object {
        val OK = ValidationResult(emptyList())
    }
}

/**
 * Reglas para no guardar registros incorrectos (RF de integridad de datos):
 * valores numericos validos, dentro de limites fisicos, lectura reciente y no repetida.
 */
object MeasurementValidator {
    /** Una lectura con mas antiguedad que esto ya no representa la muestra actual. */
    const val MAX_READING_AGE_MS = 15_000L

    /** Tolerancia para relojes desfasados: una lectura no puede venir "del futuro". */
    const val MAX_FUTURE_SKEW_MS = 60_000L

    fun validate(
        reading: MilkQualityReading,
        nowMs: Long,
        lastRecordedTimestamp: Long? = null
    ): ValidationResult {
        val errors = mutableListOf<String>()

        checkValue("Temperatura", reading.temperature, SensorLimits.TEMPERATURE, "°C")?.let { errors += it }
        checkValue("pH", reading.ph, SensorLimits.PH, "")?.let { errors += it }
        checkValue("Conductividad", reading.conductivity, SensorLimits.CONDUCTIVITY, "mS/cm")?.let { errors += it }
        checkValue("Turbidez", reading.turbidity, SensorLimits.TURBIDITY, "%")?.let { errors += it }

        when {
            reading.timestamp > nowMs + MAX_FUTURE_SKEW_MS ->
                errors += "La lectura tiene una hora futura: revisa la hora del teléfono."
            nowMs - reading.timestamp > MAX_READING_AGE_MS ->
                errors += "La lectura tiene más de ${MAX_READING_AGE_MS / 1000} s: espera una lectura nueva."
        }

        if (lastRecordedTimestamp != null && reading.timestamp == lastRecordedTimestamp) {
            errors += "Esta lectura ya fue registrada: espera la siguiente."
        }
        return if (errors.isEmpty()) ValidationResult.OK else ValidationResult(errors)
    }

    private fun checkValue(name: String, value: Float, limits: ClosedFloatingPointRange<Float>, unit: String): String? {
        val u = if (unit.isEmpty()) "" else " $unit"
        return when {
            value.isNaN() || value.isInfinite() -> "$name sin valor numérico válido."
            value !in limits -> "$name fuera de los límites del sensor (${limits.start} a ${limits.endInclusive}$u): $value$u."
            else -> null
        }
    }

    /** Nombre de estanque: 1 a 40 caracteres, letras, numeros, espacios y . _ - # / ° */
    private val TANK_PATTERN = Regex("^[\\p{L}\\p{N} ._#/°-]{1,40}$")

    /** Devuelve un mensaje si el nombre no es valido, o null si esta bien. */
    fun validateTankName(name: String): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Escribe el nombre del estanque (por ejemplo, Estanque 1)."
            clean.length > 40 -> "El nombre del estanque no puede superar 40 caracteres."
            !TANK_PATTERN.matches(clean) -> "Usa solo letras, números, espacios y . _ - # / ° en el nombre del estanque."
            else -> null
        }
    }
}

/** Estado de estabilidad de las ultimas lecturas. */
data class StabilityState(
    val isStable: Boolean,
    /** Lecturas reunidas de las necesarias (por ejemplo 2 de 4). */
    val samples: Int,
    val required: Int
)

/**
 * Indica si las ultimas lecturas variaron poco (la muestra se estabilizo).
 * Registrar mientras el sensor aun se esta ajustando (por ejemplo, al cambiar de muestra)
 * produciria un dato incorrecto, por eso se exige estabilidad antes de registrar.
 */
class StabilityTracker(
    private val window: Int = 4,
    private val maxTempSpread: Float = 0.5f,
    private val maxPhSpread: Float = 0.05f,
    private val maxCondSpread: Float = 0.15f,
    private val maxTurbSpread: Float = 2.0f
) {
    private val recent = ArrayDeque<MilkQualityReading>()

    fun reset() = recent.clear()

    fun add(reading: MilkQualityReading): StabilityState {
        // Si cambia el origen (DEMO <-> sensor) se empieza de cero.
        if (recent.isNotEmpty() && recent.last().source != reading.source) recent.clear()
        recent.addLast(reading)
        while (recent.size > window) recent.removeFirst()
        return state()
    }

    fun state(): StabilityState {
        if (recent.size < window) return StabilityState(false, recent.size, window)
        fun spread(selector: (MilkQualityReading) -> Float): Float {
            val values = recent.map(selector)
            return values.max() - values.min()
        }
        val stable = spread { it.temperature } <= maxTempSpread &&
            spread { it.ph } <= maxPhSpread &&
            spread { it.conductivity } <= maxCondSpread &&
            spread { it.turbidity } <= maxTurbSpread
        return StabilityState(stable, recent.size, window)
    }
}
