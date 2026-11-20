package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.QualityRangesHolder

/** Analisis de la medicion mas reciente de un lote, considerando las anteriores del mismo lote. */
data class LotAnalysis(
    val lotId: String,
    val analysis: MilkAnalysis,
    val latestTimestamp: Long,
    /** Mediciones guardadas del lote que se consideraron. */
    val readings: Int,
    /** Momento (ms) en que convenia volver a medir el lote. */
    val dueAtMillis: Long = latestTimestamp,
    /** Ya paso el momento en que convenia volver a medir. */
    val overdue: Boolean = false
)

/** Resume el estado de cada lote para la seccion de analisis historico del encargado. */
object LotAnalyzer {

    /**
     * Agrupa las mediciones por lote y analiza la mas reciente de cada uno.
     * Devuelve primero los lotes con mayor prioridad y, a igual prioridad, los mas recientes.
     */
    fun analyze(
        readings: List<AnalysisReading>,
        ranges: QualityRanges = QualityRangesHolder.current,
        config: AnalysisConfig = AnalysisConfig(),
        maxLots: Int = 20,
        nowMillis: Long = System.currentTimeMillis()
    ): List<LotAnalysis> = readings
        .filter { it.lotId != null }
        .groupBy { it.lotId!! }
        .map { (lotId, list) ->
            val sorted = list.sortedBy { it.timestamp }
            val latest = sorted.last()
            val analysis = MilkAnalyzer.analyze(latest, sorted.dropLast(1), ranges, config)
            val dueIn = analysis.recheckInMinutes ?: config.maxMinutesBetweenMeasurements
            val due = latest.timestamp + dueIn * 60_000L
            LotAnalysis(
                lotId = lotId,
                analysis = analysis,
                latestTimestamp = latest.timestamp,
                readings = list.size,
                dueAtMillis = due,
                overdue = nowMillis > due
            )
        }
        .sortedWith(compareByDescending<LotAnalysis> { it.analysis.priority }.thenByDescending { it.latestTimestamp })
        .take(maxLots)
}
