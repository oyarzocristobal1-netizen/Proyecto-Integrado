package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.QualityRangesHolder

/** Analisis de la medicion mas reciente de un lote, considerando las anteriores del mismo lote. */
data class LotAnalysis(
    val lotId: String,
    val analysis: MilkAnalysis,
    val latestTimestamp: Long,
    /** Mediciones guardadas del lote que se consideraron. */
    val readings: Int
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
        maxLots: Int = 20
    ): List<LotAnalysis> = readings
        .filter { it.lotId != null }
        .groupBy { it.lotId!! }
        .map { (lotId, list) ->
            val sorted = list.sortedBy { it.timestamp }
            val latest = sorted.last()
            LotAnalysis(
                lotId = lotId,
                analysis = MilkAnalyzer.analyze(latest, sorted.dropLast(1), ranges, config),
                latestTimestamp = latest.timestamp,
                readings = list.size
            )
        }
        .sortedWith(compareByDescending<LotAnalysis> { it.analysis.priority }.thenByDescending { it.latestTimestamp })
        .take(maxLots)
}
