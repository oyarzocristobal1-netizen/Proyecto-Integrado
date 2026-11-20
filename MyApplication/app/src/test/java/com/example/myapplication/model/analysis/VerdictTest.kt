package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T0 = 10 * 3_600_000L
private const val MIN = 60_000L

private fun rd(
    min: Int = 0, t: Float = 3.5f, ph: Float = 6.7f, cond: Float = 4.8f, turb: Float = 85f, lot: String? = "lote"
) = AnalysisReading(T0 + min * MIN, t, ph, cond, turb, ReadingSource.SIMULATED, lot)

class VerdictTest {

    private val ranges = QualityRanges()

    private fun verdictOf(current: AnalysisReading, previous: List<AnalysisReading> = emptyList()) =
        MilkAnalyzer.analyze(current, previous, ranges).verdict()

    @Test
    fun muestraNormalEsBuenaCalidadConPasosSimples() {
        val v = verdictOf(rd())
        assertEquals(VerdictLevel.GOOD, v.level)
        assertEquals("Buena calidad", v.title)
        assertTrue(v.message.contains("solo"))
        assertEquals(2, v.steps.size)
    }

    @Test
    fun desviacionLeveEsRevisar() {
        val v = verdictOf(rd(ph = 6.85f))
        assertEquals(VerdictLevel.REVIEW, v.level)
        assertTrue(v.steps.isNotEmpty())
    }

    @Test
    fun desviacionModeradaEsRevisarAntesDeUsar() {
        val v = verdictOf(rd(ph = 7.0f))
        assertEquals(VerdictLevel.REVIEW, v.level)
        assertEquals("Revisar antes de usar", v.title)
        assertTrue(v.steps.any { it.startsWith("Vuelva a medir en") })
    }

    @Test
    fun condicionCriticaEsNoUsarHastaVerificar() {
        val v = verdictOf(rd(t = 27f, ph = 7.3f, cond = 7.5f, turb = 30f))
        assertEquals(VerdictLevel.STOP, v.level)
        assertTrue(v.steps.first().contains("satisfactoria"))
        assertTrue(v.steps.size <= 4)
    }

    @Test
    fun lecturaDudosaNoEsNiBuenaNiMala() {
        val v = verdictOf(rd(cond = 0.1f))
        assertEquals(VerdictLevel.REVIEW, v.level)
        assertEquals("Medición no confiable", v.title)
        assertTrue(v.steps.any { it.contains("Vuelva a medir") })
    }

    @Test
    fun conductividadAltaNoDiagnosticaEnElVeredicto() {
        val v = verdictOf(rd(cond = 6.4f))
        assertFalse(v.message.lowercase().contains("mastitis"))
        assertFalse(v.steps.joinToString(" ").lowercase().contains("tiene mastitis"))
    }

    @Test
    fun unaSalidaPrevistaApareceEnElMensaje() {
        val v = verdictOf(rd(60, cond = 5.2f), listOf(rd(0, cond = 4.6f), rd(30, cond = 4.9f)))
        assertEquals(VerdictLevel.REVIEW, v.level)
        assertTrue(v.message.contains("saldría del rango"))
    }

    @Test
    fun glosarioTieneLosCuatroParametrosConLosRangosVigentes() {
        val entries = ParameterGlossary.entries(QualityRanges(tempMax = 5f, phMin = 6.5f, phMax = 6.9f, condMin = 3.5f, condMax = 6.0f, turbMin = 40f))
        assertEquals(listOf("Temperatura", "pH", "Conductividad", "Turbidez"), entries.map { it.title })
        assertTrue(entries[0].text.contains("%.1f".format(5.0f)))
        assertTrue(entries[2].text.contains("no la diagnostica"))
        assertTrue(entries[3].text.contains("No mide el espesor ni la viscosidad"))
    }
}
