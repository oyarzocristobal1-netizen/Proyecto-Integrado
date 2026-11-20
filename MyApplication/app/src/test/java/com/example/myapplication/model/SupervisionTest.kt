package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class SupervisionTest {

    private fun p(milker: String, lot: String, t: Long, status: String, temp: Double = 3.0, ph: Double = 6.7, sim: Boolean = true) =
        SupervisionPoint(milker, lot, t, status, sim, temp, ph, 4.8, 80.0)

    @Test
    fun agrupaPorLechero_cuentaYPorcentajeConforme() {
        val points = listOf(
            p("Ana", "l1", 100, "verde"), p("Ana", "l1", 200, "rojo"),
            p("Luis", "l2", 300, "verde")
        )
        val stats = SupervisionStats.groupBy(points) { it.milkerName }
        assertEquals(listOf("Ana", "Luis"), stats.map { it.name })
        assertEquals(2, stats[0].count)
        assertEquals(50.0, stats[0].compliantPercent, 0.001)
        assertEquals(200L, stats[0].lastMillis)
        assertEquals(100.0, stats[1].compliantPercent, 0.001)
    }

    @Test
    fun promediosPorGrupo() {
        val stats = SupervisionStats.groupBy(
            listOf(p("Ana", "l1", 1, "verde", temp = 3.0, ph = 6.6), p("Ana", "l1", 2, "verde", temp = 4.0, ph = 6.8))
        ) { it.milkerName }
        assertEquals(3.5, stats.single().avgTemperature, 0.001)
        assertEquals(6.7, stats.single().avgPh, 0.001)
    }

    @Test
    fun ordenaPorCantidadYDespuesPorNombre() {
        val points = listOf(p("Zoe", "a", 1, "verde"), p("Ana", "a", 2, "verde"), p("Bea", "a", 3, "verde"), p("Bea", "a", 4, "verde"))
        assertEquals(listOf("Bea", "Ana", "Zoe"), SupervisionStats.groupBy(points) { it.milkerName }.map { it.name })
    }

    @Test
    fun agrupaPorEstanqueUsandoElMapaDeLotes() {
        val tank = mapOf("l1" to "Estanque 1", "l2" to "Estanque 1", "l3" to "Estanque 2")
        val stats = SupervisionStats.groupBy(
            listOf(p("Ana", "l1", 1, "verde"), p("Luis", "l2", 2, "rojo"), p("Ana", "l3", 3, "verde"))
        ) { tank[it.lotId] ?: it.lotId }
        assertEquals(2, stats.first { it.name == "Estanque 1" }.count)
        assertEquals(50.0, stats.first { it.name == "Estanque 1" }.compliantPercent, 0.001)
    }

    @Test
    fun nombreVacioQuedaComoSinNombreYSeContabilizanLasSimuladas() {
        val stats = SupervisionStats.groupBy(listOf(p("", "l", 1, "verde", sim = true), p("", "l", 2, "verde", sim = false))) { it.milkerName }
        assertEquals("Sin nombre", stats.single().name)
        assertEquals(1, stats.single().simulatedCount)
    }

    @Test
    fun sinMedicionesNoHayGrupos() {
        assertTrue(SupervisionStats.groupBy(emptyList()) { it.milkerName }.isEmpty())
    }

    @Test
    fun sinceFiltraDesdeElInicio() {
        val points = listOf(p("a", "l", 99, "verde"), p("a", "l", 100, "verde"), p("a", "l", 101, "verde"))
        assertEquals(2, SupervisionStats.since(points, 100).size)
    }

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = Date(1_794_600_000_000L) // 13/11/2026 UTC

    @Test
    fun resumenDiarioConLotesYAvisoDeSimuladas() {
        val text = DailySummary.build(
            farm = "Fundo San Carlos", date = day,
            lots = listOf(
                DailyLotLine("Estanque 1", 3, "Buena calidad", "hace 5 min", 0, "Apto para venta"),
                DailyLotLine("Estanque 2", 2, "No usar hasta verificar", "hace 1 h", 2, null)
            ),
            totalCount = 5, compliantPercent = 60.0, pendingAlerts = 2, simulatedCount = 5,
            generatedBy = "Leandro (Encargado)", timeZone = utc
        )
        assertTrue(text.startsWith("Resumen del día 13/11/2026 - Fundo San Carlos"))
        assertTrue(text.contains("Mediciones: 5 · Conformes: 60 %"))
        assertTrue(text.contains("• Estanque 1: 3 medición(es)"))
        assertTrue(text.contains("Último análisis: Buena calidad · última medición hace 5 min · Apto para venta"))
        assertTrue(text.contains("2 alerta(s) pendiente(s)"))
        assertTrue(text.contains("SIMULADA"))
        assertTrue(text.trimEnd().endsWith("Generado por Leandro (Encargado) desde la app de calidad de la leche."))
    }

    @Test
    fun resumenDiarioSinMedicionesNoInventaNada() {
        val text = DailySummary.build("Fundo", day, emptyList(), 0, null, 0, 0, "Ana", utc)
        assertTrue(text.contains("Hoy no hay mediciones registradas."))
        assertTrue(!text.contains("SIMULADA"))
        assertTrue(!text.contains("Conformes"))
    }

    @Test
    fun resumenDiarioSinSimuladasNoAvisa() {
        val text = DailySummary.build("Fundo", day, listOf(DailyLotLine("E1", 1, null, null, 0, null)), 1, 100.0, 0, 0, "Ana", utc)
        assertTrue(!text.contains("AVISO"))
        assertTrue(text.contains("• E1: 1 medición(es)"))
    }
}
