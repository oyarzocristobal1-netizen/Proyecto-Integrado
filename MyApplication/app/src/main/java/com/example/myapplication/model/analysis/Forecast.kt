package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import kotlin.math.abs
import kotlin.math.roundToInt

/** Que se espera de un parametro si sigue la tendencia de las ultimas muestras. */
enum class ForecastStatus {
    /** Faltan muestras distintas o tiempo entre ellas para estimar una tendencia. */
    INSUFFICIENT_DATA,

    /** Sin cambios que lo saquen del rango en el horizonte considerado. */
    STABLE,

    /** Esta dentro del rango pero la tendencia lo llevaria fuera. */
    WILL_EXIT_RANGE,

    /** Esta fuera del rango y se acerca a el. */
    IMPROVING,

    /** Esta fuera del rango y se aleja. */
    WORSENING,

    /** Esta fuera del rango y no cambia. */
    NOT_IMPROVING
}

/**
 * Proyeccion lineal de un parametro. Es una ESTIMACION por tendencia con pocas muestras, no una
 * prediccion validada: la leche real puede cambiar a otro ritmo.
 */
data class ParameterForecast(
    val parameter: Parameter,
    val status: ForecastStatus,
    /** Cambio por hora segun las ultimas muestras, o null si no hay datos suficientes. */
    val ratePerHour: Float?,
    /** Minutos hasta cruzar (o alcanzar) el limite del rango, o null si no aplica. */
    val minutesToEvent: Int?,
    val headline: String,
    val detail: String,
    /** Muestras distintas usadas para estimar la tendencia. */
    val samples: Int
) {
    /** Conviene mostrarla al usuario (no es "sin novedad" ni "faltan datos"). */
    val isNotable: Boolean get() = status != ForecastStatus.STABLE && status != ForecastStatus.INSUFFICIENT_DATA
}

/** Resultado de proyectar los cuatro parametros. */
data class ForecastResult(
    val forecasts: List<ParameterForecast>,
    /** Aviso cuando no se puede proyectar (por ejemplo faltan mediciones), o null. */
    val note: String?
) {
    val notable: List<ParameterForecast> get() = forecasts.filter { it.isNotable }
    val soonestExit: ParameterForecast?
        get() = forecasts.filter { it.status == ForecastStatus.WILL_EXIT_RANGE }.minByOrNull { it.minutesToEvent ?: Int.MAX_VALUE }
}

/** Proyecta hacia donde va cada parametro usando una recta ajustada a las ultimas muestras del lote. */
object ForecastEngine {

    private const val EPS = 1e-3f

    private data class Limits(val lower: Float?, val upper: Float?) {
        fun inside(v: Float) = (lower == null || v >= lower - EPS) && (upper == null || v <= upper + EPS)
    }

    private fun limits(p: Parameter, r: QualityRanges) = when (p) {
        Parameter.TEMPERATURE -> Limits(null, r.tempMax)
        Parameter.PH -> Limits(r.phMin, r.phMax)
        Parameter.CONDUCTIVITY -> Limits(r.condMin, r.condMax)
        Parameter.TURBIDITY -> Limits(r.turbMin, null)
    }

    fun forecast(
        current: AnalysisReading,
        previous: List<AnalysisReading>,
        r: QualityRanges,
        c: AnalysisConfig
    ): ForecastResult {
        val usable = previous.filter {
            it.timestamp < current.timestamp &&
                it.source == current.source &&
                it.lotId == current.lotId &&
                ReliabilityChecker.check(it, r, c, null).isEmpty()
        }
        val ordered = (usable + current).sortedBy { it.timestamp }
        // Varias lecturas de la misma muestra (pocos minutos) cuentan una sola vez.
        val clusters = mutableListOf<MutableList<AnalysisReading>>()
        for (reading in ordered) {
            val last = clusters.lastOrNull()?.last()
            if (last != null && reading.timestamp - last.timestamp <= c.sameSampleWindowMs) clusters.last().add(reading)
            else clusters.add(mutableListOf(reading))
        }
        val samples = clusters.map { it.last() }.takeLast(c.trendWindow)
        val spanMinutes = if (samples.size >= 2) (samples.last().timestamp - samples.first().timestamp) / 60_000.0 else 0.0

        val enough = samples.size >= c.minSamplesForTrend && spanMinutes >= c.forecastMinSpanMinutes
        if (!enough) {
            val note = when {
                usable.isEmpty() -> "Aún no hay mediciones anteriores de este lote para proyectar."
                samples.size < c.minSamplesForTrend ->
                    "Para proyectar se necesitan al menos ${c.minSamplesForTrend} muestras distintas del lote (hay ${samples.size})."
                else -> "Las muestras están muy juntas en el tiempo (${spanMinutes.roundToInt()} min); se necesita al menos " +
                    "${c.forecastMinSpanMinutes} min entre la primera y la última para proyectar."
            }
            val insufficient = Parameter.entries.map {
                ParameterForecast(it, ForecastStatus.INSUFFICIENT_DATA, null, null, "${it.label}: sin datos suficientes", note, samples.size)
            }
            return ForecastResult(insufficient, note)
        }

        val forecasts = Parameter.entries.map { p -> forecastOne(p, samples, spanMinutes, r, c) }
        return ForecastResult(forecasts, null)
    }

    private fun forecastOne(
        p: Parameter,
        samples: List<AnalysisReading>,
        spanMinutes: Double,
        r: QualityRanges,
        c: AnalysisConfig
    ): ParameterForecast {
        val slope = slopePerHour(p, samples)
        val value = samples.last().value(p)
        val lim = limits(p, r)
        val totalChange = abs(slope * (spanMinutes / 60.0)).toFloat()
        val n = samples.size
        val rate = rateText(p, slope)

        if (totalChange < c.trendMinChange[p]) {
            return if (lim.inside(value)) {
                ParameterForecast(p, ForecastStatus.STABLE, slope.toFloat(), null, "${p.label}: sin cambios previstos",
                    "Se mantiene estable según las últimas ${n} muestras.", n)
            } else {
                ParameterForecast(p, ForecastStatus.NOT_IMPROVING, slope.toFloat(), null, "${p.label} sigue fuera del rango",
                    "No muestra cambios en las últimas $n muestras: no se acerca al rango por sí sola. ${note(p, ForecastStatus.NOT_IMPROVING)}", n)
            }
        }

        val rising = slope > 0
        fun minutesTo(limit: Float): Int = (((limit - value) / slope) * 60.0).roundToInt().coerceAtLeast(0)

        return if (lim.inside(value)) {
            val limit = if (rising) lim.upper else lim.lower
            val minutes = limit?.let { minutesTo(it) }
            if (limit != null && minutes != null && minutes <= c.forecastHorizonMinutes) {
                ParameterForecast(
                    p, ForecastStatus.WILL_EXIT_RANGE, slope.toFloat(), minutes,
                    "${p.label} saldría del rango en ${formatMinutes(minutes)}",
                    "A este ritmo ($rate) llegaría a ${formatValue(p, limit)} en ${formatMinutes(minutes)}. ${note(p, ForecastStatus.WILL_EXIT_RANGE)}",
                    n
                )
            } else {
                ParameterForecast(p, ForecastStatus.STABLE, slope.toFloat(), null, "${p.label}: sin riesgo próximo",
                    "Cambia ($rate) pero no saldría del rango en las próximas ${formatMinutes(c.forecastHorizonMinutes)} a este ritmo.", n)
            }
        } else {
            val above = lim.upper != null && value > lim.upper
            val toward = (above && !rising) || (!above && rising)
            if (toward) {
                val limit = if (above) lim.upper!! else lim.lower!!
                val minutes = minutesTo(limit)
                val within = minutes <= c.forecastHorizonMinutes
                ParameterForecast(
                    p, ForecastStatus.IMPROVING, slope.toFloat(), minutes,
                    if (within) "${p.label} volvería al rango en ${formatMinutes(minutes)}" else "${p.label} mejora, pero lentamente",
                    "A este ritmo ($rate) llegaría a ${formatValue(p, limit)} en ${formatMinutes(minutes)}. ${note(p, ForecastStatus.IMPROVING)}",
                    n
                )
            } else {
                ParameterForecast(p, ForecastStatus.WORSENING, slope.toFloat(), null, "${p.label} se aleja del rango",
                    "Sigue fuera del rango y empeora ($rate). ${note(p, ForecastStatus.WORSENING)}", n)
            }
        }
    }

    /** Pendiente de la recta de minimos cuadrados, en unidades por hora. */
    fun slopePerHour(p: Parameter, samples: List<AnalysisReading>): Double {
        val t0 = samples.first().timestamp
        val xs = samples.map { (it.timestamp - t0) / 3_600_000.0 }
        val ys = samples.map { it.value(p).toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return 0.0
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
    }

    private fun rateText(p: Parameter, slope: Double): String {
        val unit = if (p.unit.isEmpty()) "" else " ${p.unit}"
        val decimals = when (p) {
            Parameter.PH -> 2
            Parameter.CONDUCTIVITY -> 2
            else -> 1
        }
        return "${"%+.${decimals}f".format(slope)}$unit por hora"
    }

    private fun note(p: Parameter, status: ForecastStatus): String = when (p) {
        Parameter.TEMPERATURE ->
            "Es una estimación lineal: el enfriamiento real se hace más lento al acercarse a la temperatura del tanque, así que puede tardar más."
        Parameter.CONDUCTIVITY ->
            if (status == ForecastStatus.IMPROVING) {
                "Confirme con una nueva medición."
            } else {
                "La conductividad sirve como señal de seguimiento de la calidad, pero no diagnostica enfermedades ni define por sí sola si la leche se puede vender: confirme con una nueva medición y, si persiste, con controles adicionales o evaluación veterinaria."
            }
        else -> "Confirme con una nueva medición."
    }

    fun formatMinutes(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
