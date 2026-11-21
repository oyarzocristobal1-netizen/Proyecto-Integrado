package com.example.myapplication.model

import com.example.myapplication.model.analysis.AnalysisReading
import com.example.myapplication.model.analysis.LotAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyDayTest {
    private fun point(t: Long, status: String = "verde", simulated: Boolean = false) = SupervisionPoint(
        milkerName = "Ana", lotId = "L1", epochMillis = t, status = status, simulated = simulated,
        temperature = 4.0, ph = 6.7, conductivity = 4.8, turbidity = 80.0
    )

    @Test
    fun sinMedicionesHoyNoHayPorcentaje() {
        val s = MyDay.summarize(listOf(point(500)), dayStartMillis = 1000)
        assertEquals(0, s.count)
        assertNull(s.compliantPercent)
        assertNull(s.lastMillis)
    }

    @Test
    fun cuentaConformesUltimaYSimuladas() {
        val s = MyDay.summarize(
            listOf(point(1000), point(2000, "rojo", simulated = true), point(3000, "ambar"), point(4000, "verde", simulated = true), point(10)),
            dayStartMillis = 1000
        )
        assertEquals(4, s.count)
        assertEquals(2, s.compliantCount)
        assertEquals(50.0, s.compliantPercent!!, 0.001)
        assertEquals(4000L, s.lastMillis)
        assertEquals(2, s.simulatedCount)
    }

    @Test
    fun inicioDelDiaEsMedianocheLocal() {
        val now = 1_700_000_000_000L
        val start = MyDay.startOfDay(now)
        assertTrue(start <= now)
        assertTrue(now - start < 24 * 60 * 60 * 1000L)
        assertEquals(start, MyDay.startOfDay(start))
    }

    private fun reading(lot: String, ts: Long, temp: Float = 4f) = AnalysisReading(
        timestamp = ts, temperature = temp, ph = 6.7f, conductivity = 4.8f, turbidity = 80f,
        source = com.example.myapplication.model.ReadingSource.SIMULATED, lotId = lot
    )

    @Test
    fun recordatoriosSeparanAtrasadosYProximos() {
        val now = 10_000_000_000L
        val analyses = LotAnalyzer.analyze(
            listOf(reading("L1", now - 13 * 3_600_000L), reading("L2", now - 60_000L), reading("L3", now - 60_000L)),
            nowMillis = now
        )
        val plan = Reminders.plan(analyses, { "T-$it" }, eligibleLotIds = setOf("L1", "L2"), nowMillis = now)
        // L3 no es elegible (cerrado o de otro dia)
        assertTrue(plan.overdue.none { it.lotId == "L3" } && plan.upcoming.none { it.lotId == "L3" })
        assertTrue(plan.overdue.any { it.lotId == "L1" })
        assertTrue(plan.upcoming.any { it.lotId == "L2" })
        assertEquals("T-L2", plan.upcoming.first { it.lotId == "L2" }.tank)
        assertTrue(plan.overdue.all { it.dueAtMillis <= now })
        assertTrue(plan.upcoming.all { it.dueAtMillis > now })
    }

    @Test
    fun noSeProgramanAvisosMuyLejanos() {
        val now = 10_000_000_000L
        val analyses = LotAnalyzer.analyze(listOf(reading("L1", now)), nowMillis = now)
        val plan = Reminders.plan(analyses, { it }, setOf("L1"), now, horizonMillis = 1000)
        assertTrue(plan.upcoming.isEmpty())
        assertTrue(plan.overdue.isEmpty())
    }

    @Test
    fun textoDelAvisoNombraElLote() {
        assertTrue(Reminders.notificationText("Estanque 2").contains("Estanque 2"))
    }

    @Test
    fun notaDeRevisionHastaDoscientosCaracteres() {
        assertNull(ReviewRules.validateNote(""))
        assertNull(ReviewRules.validateNote("a".repeat(200)))
        assertTrue(ReviewRules.validateNote("a".repeat(201)) != null)
        assertEquals("hola", ReviewRules.clean("  hola \n"))
    }
}
