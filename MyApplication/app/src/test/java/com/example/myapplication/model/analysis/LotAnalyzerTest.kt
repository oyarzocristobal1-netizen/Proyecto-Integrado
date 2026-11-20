package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val H = 3_600_000L

private fun r(
    ts: Long, lot: String?, t: Float = 3.8f, ph: Float = 6.7f, cond: Float = 4.8f, turb: Float = 85f
) = AnalysisReading(ts, t, ph, cond, turb, ReadingSource.SIMULATED, lot)

class LotAnalyzerTest {

    private val ranges = QualityRanges()

    @Test
    fun sinMedicionesNoHayLotes() {
        assertTrue(LotAnalyzer.analyze(emptyList(), ranges).isEmpty())
    }

    @Test
    fun medicionesSinLoteSeIgnoran() {
        assertTrue(LotAnalyzer.analyze(listOf(r(H, null, ph = 7.2f)), ranges).isEmpty())
    }

    @Test
    fun cadaLoteSeAnalizaConSuUltimaMedicion() {
        val result = LotAnalyzer.analyze(
            listOf(r(1 * H, "A", ph = 7.2f), r(2 * H, "A"), r(3 * H, "B", t = 9f)),
            ranges
        )
        assertEquals(2, result.size)
        val a = result.first { it.lotId == "A" }
        assertEquals(2, a.readings)
        assertEquals(2 * H, a.latestTimestamp)
        // La ultima medicion de A es normal; el pH alto anterior solo aparece como historial.
        assertTrue(a.analysis.altered.isEmpty())
    }

    @Test
    fun ordenaPorPrioridadYLuegoPorFecha() {
        val result = LotAnalyzer.analyze(
            listOf(
                r(5 * H, "normal"),
                r(4 * H, "grave", t = 27f, ph = 7.3f, cond = 7.5f, turb = 30f),
                r(6 * H, "leve", ph = 6.85f)
            ),
            ranges
        )
        assertEquals(listOf("grave", "leve", "normal"), result.map { it.lotId })
        assertEquals(PriorityLevel.CRITICAL, result.first().analysis.priority)
    }

    @Test
    fun noMezclaMedicionesDeOtroLote() {
        // Tres mediciones con pH alto en el lote B no deben volver "persistente" al lote A.
        val readings = listOf(
            r(1 * H, "B", ph = 7.0f), r(2 * H, "B", ph = 7.0f), r(3 * H, "B", ph = 7.0f),
            r(4 * H, "A", ph = 7.0f)
        )
        val a = LotAnalyzer.analyze(readings, ranges).first { it.lotId == "A" }
        assertTrue(a.analysis.historyCards.isEmpty())
        assertEquals(1, a.readings)
    }

    @Test
    fun respetaElMaximoDeLotes() {
        val readings = (1..5).map { r(it * H, "L$it") }
        assertEquals(3, LotAnalyzer.analyze(readings, ranges, maxLots = 3).size)
    }

    @Test
    fun medicionActualSinLoteNoUsaHistorialDeLotes() {
        val previous = listOf(r(1 * H, "A", ph = 7.0f), r(2 * H, "A", ph = 7.0f), r(3 * H, "A", ph = 7.0f))
        val analysis = MilkAnalyzer.analyze(r(4 * H, null, ph = 7.0f), previous, ranges)
        assertTrue(analysis.historyCards.isEmpty())
    }

    @Test
    fun loteNormalSeMarcaAtrasadoPasadoElTiempoMaximoSinMedir() {
        val config = AnalysisConfig()
        val last = 5 * H
        val onTime = LotAnalyzer.analyze(listOf(r(last, "A")), ranges, config, nowMillis = last + 60_000L).first()
        assertTrue(!onTime.overdue)
        val late = LotAnalyzer.analyze(
            listOf(r(last, "A")), ranges, config,
            nowMillis = last + (config.maxMinutesBetweenMeasurements + 1) * 60_000L
        ).first()
        assertTrue(late.overdue)
        assertEquals(last + config.maxMinutesBetweenMeasurements * 60_000L, late.dueAtMillis)
    }

    @Test
    fun loteConProblemaDebeRemedirseAntes() {
        val config = AnalysisConfig()
        val last = 5 * H
        val item = LotAnalyzer.analyze(
            listOf(r(last, "A", ph = 7.0f)), ranges, config,
            nowMillis = last + (config.recheckWarningMinutes + 1) * 60_000L
        ).first()
        assertTrue(item.overdue)
        assertEquals(last + config.recheckWarningMinutes * 60_000L, item.dueAtMillis)
    }
}

