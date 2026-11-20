package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORIGIN = 10 * 3_600_000L
private const val M = 60_000L

private fun s(
    min: Int, t: Float = 3.5f, ph: Float = 6.7f, cond: Float = 4.8f, turb: Float = 85f,
    lot: String? = "lote", source: ReadingSource = ReadingSource.SIMULATED
) = AnalysisReading(ORIGIN + min * M, t, ph, cond, turb, source, lot)

class ProgressAnalyzerTest {

    private val ranges = QualityRanges()

    @Test
    fun sinMuestraAnteriorNoHayComparacion() {
        assertNull(ProgressAnalyzer.compare(s(60), emptyList(), ranges))
    }

    @Test
    fun laMismaMuestraNoSeCompara() {
        assertNull(ProgressAnalyzer.compare(s(5, t = 6f), listOf(s(0, t = 8f)), ranges))
    }

    @Test
    fun siTodoEstabaYEstaEnRangoNoHayNadaQueComparar() {
        assertNull(ProgressAnalyzer.compare(s(60), listOf(s(0)), ranges))
    }

    @Test
    fun temperaturaQueBajaDespuesDeActuarMejora() {
        val report = ProgressAnalyzer.compare(s(60, t = 4.5f), listOf(s(0, t = 7.0f)), ranges)!!
        assertEquals(ProgressTrend.IMPROVED, report.overall)
        assertEquals(60L, report.minutesBetween)
        val temp = report.items.first { it.parameter == Parameter.TEMPERATURE }
        assertEquals(ProgressTrend.IMPROVED, temp.trend)
        // El separador decimal depende del idioma del equipo (coma en Chile, punto en inglés).
        assertTrue(temp.previousText.contains("%.1f".format(7.0f)) && temp.currentText.contains("%.1f".format(4.5f)))
        assertTrue(report.headline.startsWith("Mejoró"))
    }

    @Test
    fun volverAlRangoEsMejora() {
        val report = ProgressAnalyzer.compare(s(60), listOf(s(0, cond = 6.4f)), ranges)!!
        assertEquals(ProgressTrend.IMPROVED, report.overall)
    }

    @Test
    fun pasarDeNormalAAlteradoEsEmpeora() {
        val report = ProgressAnalyzer.compare(s(60, ph = 7.1f), listOf(s(0)), ranges)!!
        assertEquals(ProgressTrend.WORSENED, report.overall)
        assertTrue(report.headline.startsWith("Empeoró"))
    }

    @Test
    fun igualDeAlteradoSinCambioEsSinCambios() {
        val report = ProgressAnalyzer.compare(s(60, t = 7f), listOf(s(0, t = 7f)), ranges)!!
        assertEquals(ProgressTrend.SAME, report.overall)
    }

    @Test
    fun siSigueAlteradoPeroMenosCuentaComoMejora() {
        val report = ProgressAnalyzer.compare(s(60, t = 6f), listOf(s(0, t = 8f)), ranges)!!
        assertEquals(ProgressTrend.IMPROVED, report.overall)
    }

    @Test
    fun ignoraOtroLoteYOtraFuente() {
        val others = listOf(s(0, t = 8f, lot = "otro"), s(0, t = 8f, source = ReadingSource.DEVICE))
        assertNull(ProgressAnalyzer.compare(s(60, t = 4.5f), others, ranges))
    }

    @Test
    fun conLecturaDudosaNoComparaNiUsaComoBase() {
        assertNull(ProgressAnalyzer.compare(s(60, cond = 0.1f), listOf(s(0, t = 8f)), ranges))
        val report = ProgressAnalyzer.compare(s(120, t = 5f), listOf(s(0, t = 8f), s(60, cond = 0.1f)), ranges)
        assertNotNull(report)
        assertEquals(120L, report!!.minutesBetween)
    }
}
