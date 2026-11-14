package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class DashboardStatsTest {

    private val chile = TimeZone.getTimeZone("America/Santiago")

    // 13/11/2026 17:00 en Chile
    private val now = 1_794_600_000_000L
    private val hora = 3_600_000L
    private val dia = 24 * hora

    private fun p(millis: Long, ph: Double = 6.7, t: Double = 3.8, status: String = "verde", sim: Boolean = true) =
        MeasurementPoint(millis, t, ph, status, sim)

    @Test
    fun sinMedicionesNoHayPorcentajeNiUltimaMedicion() {
        val s = DashboardStats.compute(emptyList(), now, chile)
        assertEquals(0, s.todayCount)
        assertNull(s.todayCompliantPercent)
        assertNull(s.lastMeasurementMillis)
        assertEquals(7, s.days.size)
        assertEquals(0, s.days.sumOf { it.count })
    }

    @Test
    fun cuentaSoloLasDeHoyYCalculaConformes() {
        val puntos = listOf(
            p(now - 1 * hora),
            p(now - 2 * hora, status = "rojo"),
            p(now - 3 * hora, status = "ambar"),
            p(now - 4 * hora),
            p(now - dia) // ayer
        )
        val s = DashboardStats.compute(puntos, now, chile)
        assertEquals(4, s.todayCount)
        assertEquals(50.0, s.todayCompliantPercent!!, 0.001)
        assertEquals(now - hora, s.lastMeasurementMillis)
        assertEquals(5, s.totalCount)
    }

    @Test
    fun laMedianocheLocalSeparaLosDias() {
        // Hoy 00:30 en Chile cuenta como hoy; ayer 23:30, como ayer
        val hoy0030 = now - 16 * hora - 30 * 60_000L
        val ayer2330 = now - 17 * hora - 30 * 60_000L
        val s = DashboardStats.compute(listOf(p(hoy0030), p(ayer2330)), now, chile)
        assertEquals(1, s.todayCount)
        assertEquals(1, s.days.last().count)
        assertEquals(1, s.days[5].count)
    }

    @Test
    fun promediosDiariosParaLaTendencia() {
        val puntos = listOf(p(now - hora, ph = 6.6, t = 4.0), p(now - 2 * hora, ph = 6.8, t = 6.0), p(now - 2 * dia, ph = 7.1))
        val s = DashboardStats.compute(puntos, now, chile)
        assertEquals("13/11", s.days.last().label)
        assertEquals("07/11", s.days.first().label)
        assertEquals(6.7, s.days.last().avgPh!!, 0.0001)
        assertEquals(5.0, s.days.last().avgTemperature!!, 0.0001)
        assertEquals(7.1, s.days[4].avgPh!!, 0.0001)
        assertNull(s.days[5].avgPh)
    }

    @Test
    fun cuentaLasMedicionesSimuladas() {
        val s = DashboardStats.compute(listOf(p(now - hora, sim = true), p(now - 2 * hora, sim = false)), now, chile)
        assertEquals(1, s.simulatedCount)
    }
}
