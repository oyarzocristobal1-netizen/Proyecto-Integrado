package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.SensorLimits
import com.example.myapplication.model.StabilityState

/** Pequena tolerancia para no clasificar mal por el redondeo de los Float (7.0 - 6.8 = 0.20000005). */
private const val EPS = 1e-3f

/** Clasifica cada parametro segun los rangos vigentes y los margenes configurados. */
internal object ParameterClassifier {

    fun classify(p: Parameter, value: Float, r: QualityRanges, c: AnalysisConfig): ParameterFinding {
        val reference = referenceText(p, r, c)
        fun finding(level: DeviationLevel, direction: Direction, distance: Float) =
            ParameterFinding(p, value, level, direction, distance, reference)

        return when (p) {
            Parameter.TEMPERATURE ->
                if (value <= r.tempMax + EPS) finding(DeviationLevel.IN_RANGE, Direction.NONE, 0f)
                else {
                    val d = value - r.tempMax
                    val level = if (value > c.tempCriticalAbove) DeviationLevel.CRITICAL else level(d, c.tempHigh)
                    finding(level, Direction.HIGH, d)
                }

            Parameter.PH -> when {
                value < r.phMin - EPS -> (r.phMin - value).let { finding(level(it, c.phBand), Direction.LOW, it) }
                value > r.phMax + EPS -> (value - r.phMax).let { finding(level(it, c.phBand), Direction.HIGH, it) }
                else -> finding(DeviationLevel.IN_RANGE, Direction.NONE, 0f)
            }

            Parameter.CONDUCTIVITY -> when {
                value < r.condMin - EPS -> (r.condMin - value).let { finding(level(it, c.condLow), Direction.LOW, it) }
                value > r.condMax + EPS -> (value - r.condMax).let { finding(level(it, c.condHigh), Direction.HIGH, it) }
                else -> finding(DeviationLevel.IN_RANGE, Direction.NONE, 0f)
            }

            Parameter.TURBIDITY -> {
                val max = c.turbidityMax
                when {
                    value < r.turbMin - EPS -> (r.turbMin - value).let { finding(level(it, c.turbLow), Direction.LOW, it) }
                    max != null && value > max + EPS -> (value - max).let { finding(level(it, c.turbHigh), Direction.HIGH, it) }
                    else -> finding(DeviationLevel.IN_RANGE, Direction.NONE, 0f)
                }
            }
        }
    }

    private fun level(distance: Float, band: Band): DeviationLevel = when {
        distance <= band.slight + EPS -> DeviationLevel.SLIGHT
        distance <= band.moderate + EPS -> DeviationLevel.MODERATE
        distance <= band.considerable + EPS -> DeviationLevel.CONSIDERABLE
        else -> DeviationLevel.CRITICAL
    }

    fun referenceText(p: Parameter, r: QualityRanges, c: AnalysisConfig): String = when (p) {
        Parameter.TEMPERATURE -> "máximo ${fmt(r.tempMax, 1)} °C"
        Parameter.PH -> "${fmt(r.phMin, 2)} a ${fmt(r.phMax, 2)}"
        Parameter.CONDUCTIVITY -> "${fmt(r.condMin, 2)} a ${fmt(r.condMax, 2)} mS/cm"
        Parameter.TURBIDITY -> c.turbidityMax?.let { "${fmt(r.turbMin, 0)} a ${fmt(it, 0)} %" } ?: "mínimo ${fmt(r.turbMin, 0)} %"
    }
}

/** Dato dudoso: a que parametros afecta y por que. */
internal data class ReliabilityIssue(val parameters: Set<Parameter>, val reason: String)

/**
 * Busca lecturas que no se pueden interpretar con confianza: valores no numericos o fuera de los
 * limites fisicos, sensor en el extremo de su escala, temperatura bajo cero, resultados
 * contradictorios o una lectura que aun no se estabiliza.
 */
internal object ReliabilityChecker {

    fun check(
        reading: AnalysisReading,
        r: QualityRanges,
        c: AnalysisConfig,
        stability: StabilityState?
    ): List<ReliabilityIssue> {
        val issues = mutableListOf<ReliabilityIssue>()
        val all = Parameter.entries.toSet()

        val values = Parameter.entries.associateWith { reading.value(it) }
        if (values.values.any { it.isNaN() || it.isInfinite() }) {
            issues += ReliabilityIssue(
                values.filterValues { it.isNaN() || it.isInfinite() }.keys,
                "Hay valores sin número válido: la trama de la caja pudo llegar incompleta o dañada."
            )
        }
        val finite = values.filterValues { !it.isNaN() && !it.isInfinite() }

        val outside = finite.filter { (p, v) -> v !in limitsOf(p) }.keys
        if (outside.isNotEmpty()) {
            issues += ReliabilityIssue(
                outside,
                "Valor fuera de los límites físicos del sensor: sensor desconectado, trama corrupta o calibración errónea."
            )
        }

        if (finite.size == 4 && finite.values.all { it == 0f }) {
            issues += ReliabilityIssue(all, "Los cuatro valores son 0: el sensor probablemente no está en contacto con la muestra.")
        } else {
            val saturated = finite.filter { (p, v) -> p != Parameter.TEMPERATURE && v in limitsOf(p) && (v == limitsOf(p).start || v == limitsOf(p).endInclusive) }.keys
            if (saturated.isNotEmpty()) {
                issues += ReliabilityIssue(
                    saturated,
                    "Valor justo en el extremo de la escala del sensor: posible saturación o sensor sin contacto con la muestra."
                )
            }
        }

        finite[Parameter.TEMPERATURE]?.let { t ->
            if (t in SensorLimits.TEMPERATURE && t < c.temperatureFloor) {
                issues += ReliabilityIssue(
                    setOf(Parameter.TEMPERATURE),
                    "Temperatura bajo ${fmt(c.temperatureFloor, 0)} °C: posible sensor mal conectado o muestra congelada."
                )
            }
        }

        val cond = finite[Parameter.CONDUCTIVITY]
        val turb = finite[Parameter.TURBIDITY]
        if (cond != null && turb != null && cond in SensorLimits.CONDUCTIVITY && cond < c.nearZeroConductivity &&
            turb >= r.turbMin && turb in SensorLimits.TURBIDITY
        ) {
            issues += ReliabilityIssue(
                setOf(Parameter.CONDUCTIVITY, Parameter.TURBIDITY),
                "Resultados contradictorios: la conductividad es casi nula pero la muestra es opaca (turbidez normal). " +
                    "Una leche real no se comporta así: posible sonda de conductividad sin contacto o con falla."
            )
        }

        if (stability != null && !stability.isStable) {
            issues += ReliabilityIssue(all, "La lectura aún no se estabiliza (${stability.samples} de ${stability.required} lecturas parecidas).")
        }
        return issues
    }

    private fun limitsOf(p: Parameter): ClosedFloatingPointRange<Float> = when (p) {
        Parameter.TEMPERATURE -> SensorLimits.TEMPERATURE
        Parameter.PH -> SensorLimits.PH
        Parameter.CONDUCTIVITY -> SensorLimits.CONDUCTIVITY
        Parameter.TURBIDITY -> SensorLimits.TURBIDITY
    }
}

internal fun fmt(v: Float, decimals: Int): String = "%.${decimals}f".format(v)

internal fun formatValue(p: Parameter, v: Float): String = when (p) {
    Parameter.TEMPERATURE -> "${fmt(v, 1)} °C"
    Parameter.PH -> fmt(v, 2)
    Parameter.CONDUCTIVITY -> "${fmt(v, 2)} mS/cm"
    Parameter.TURBIDITY -> "${fmt(v, 0)} %"
}
