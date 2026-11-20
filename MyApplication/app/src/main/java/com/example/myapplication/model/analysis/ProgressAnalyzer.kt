package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.QualityRangesHolder

enum class ProgressTrend { IMPROVED, WORSENED, SAME }

data class ProgressItem(
    val parameter: Parameter,
    val previousText: String,
    val currentText: String,
    val trend: ProgressTrend
)

/** Comparacion de una medicion con la muestra anterior del mismo lote (por ejemplo despues de actuar). */
data class ProgressReport(
    val overall: ProgressTrend,
    val headline: String,
    val items: List<ProgressItem>,
    /** Minutos entre la muestra anterior y la actual. */
    val minutesBetween: Long
)

/** Dice si la ultima medicion mejoro, empeoro o quedo igual respecto de la muestra anterior. */
object ProgressAnalyzer {

    private const val EPS = 1e-3f

    /**
     * Devuelve null si no hay una muestra anterior distinta (las lecturas tomadas con pocos minutos de
     * diferencia son la misma muestra) o si ambas estan completamente en rango (no hay nada que comparar).
     */
    fun compare(
        current: AnalysisReading,
        previous: List<AnalysisReading>,
        ranges: QualityRanges = QualityRangesHolder.current,
        config: AnalysisConfig = AnalysisConfig()
    ): ProgressReport? {
        if (ReliabilityChecker.check(current, ranges, config, null).isNotEmpty()) return null
        val before = previous
            .filter {
                it.source == current.source &&
                    it.lotId == current.lotId &&
                    it.timestamp < current.timestamp - config.sameSampleWindowMs &&
                    ReliabilityChecker.check(it, ranges, config, null).isEmpty()
            }
            .maxByOrNull { it.timestamp } ?: return null

        val items = Parameter.entries.map { p ->
            val prev = ParameterClassifier.classify(p, before.value(p), ranges, config)
            val now = ParameterClassifier.classify(p, current.value(p), ranges, config)
            val trend = when {
                now.level.rank < prev.level.rank -> ProgressTrend.IMPROVED
                now.level.rank > prev.level.rank -> ProgressTrend.WORSENED
                now.level.rank == 0 -> ProgressTrend.SAME
                now.distance < prev.distance - EPS -> ProgressTrend.IMPROVED
                now.distance > prev.distance + EPS -> ProgressTrend.WORSENED
                else -> ProgressTrend.SAME
            }
            ProgressItem(p, formatValue(p, before.value(p)), formatValue(p, current.value(p)), trend) to (prev.level.rank to now.level.rank)
        }
        val prevTotal = items.sumOf { it.second.first }
        val nowTotal = items.sumOf { it.second.second }
        if (prevTotal == 0 && nowTotal == 0) return null

        val overall = when {
            nowTotal < prevTotal -> ProgressTrend.IMPROVED
            nowTotal > prevTotal -> ProgressTrend.WORSENED
            items.any { it.first.trend == ProgressTrend.WORSENED } && items.none { it.first.trend == ProgressTrend.IMPROVED } -> ProgressTrend.WORSENED
            items.any { it.first.trend == ProgressTrend.IMPROVED } && items.none { it.first.trend == ProgressTrend.WORSENED } -> ProgressTrend.IMPROVED
            else -> ProgressTrend.SAME
        }
        val headline = when (overall) {
            ProgressTrend.IMPROVED -> "Mejoró respecto de la muestra anterior"
            ProgressTrend.WORSENED -> "Empeoró respecto de la muestra anterior"
            ProgressTrend.SAME -> "Sin cambios importantes respecto de la muestra anterior"
        }
        return ProgressReport(
            overall = overall,
            headline = headline,
            items = items.map { it.first },
            minutesBetween = (current.timestamp - before.timestamp) / 60_000L
        )
    }
}
