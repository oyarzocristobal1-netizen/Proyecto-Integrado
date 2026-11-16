package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private val ref = QualityRanges()

    private fun alerts(t: Float, ph: Float, ce: Float, tu: Float, ranges: QualityRanges = ref) =
        detectAlerts(MilkQualityReading(t, ph, ce, tu), ranges)

    @Test
    fun lecheNormalNoGeneraAlertas() {
        assertTrue(alerts(3.8f, 6.7f, 4.8f, 85f).isEmpty())
    }

    @Test
    fun temperaturaSobreElMaximo() {
        val tibia = alerts(22f, 6.7f, 4.8f, 85f).single()
        assertEquals("temperatura", tibia.type)
        assertEquals(AlertSeverity.MEDIA, tibia.severity)
        assertEquals(AlertSeverity.ALTA, alerts(30f, 6.7f, 4.8f, 85f).single().severity)
    }

    @Test
    fun phAlcalinoYAcido() {
        val alcalino = alerts(3.8f, 7.15f, 4.8f, 85f).single()
        assertEquals(AlertSeverity.ALTA, alcalino.severity)
        assertTrue(alcalino.message.contains("alcalino"))
        val acido = alerts(3.8f, 6.5f, 4.8f, 85f).single()
        assertEquals(AlertSeverity.MEDIA, acido.severity)
        assertTrue(acido.message.contains("ácido"))
    }

    @Test
    fun conductividadAltaYBaja() {
        assertEquals(AlertSeverity.MEDIA, alerts(3.8f, 6.7f, 5.8f, 85f).single().severity)
        assertEquals(AlertSeverity.ALTA, alerts(3.8f, 6.7f, 6.8f, 85f).single().severity)
        val baja = alerts(3.8f, 6.7f, 2.8f, 85f).single()
        assertEquals(AlertSeverity.ALTA, baja.severity)
        assertTrue(baja.message.contains("baja"))
    }

    @Test
    fun turbidezBajaEsSiempreAlta() {
        val a = alerts(3.8f, 6.7f, 4.8f, 35f).single()
        assertEquals("turbidez", a.type)
        assertEquals(AlertSeverity.ALTA, a.severity)
    }

    @Test
    fun muestraMastiticaGeneraTresAlertas() {
        val tipos = alerts(25f, 7.15f, 6.8f, 60f).map { it.type }
        assertEquals(listOf("temperatura", "ph", "conductividad"), tipos)
    }

    @Test
    fun respetaRangosConfigurados() {
        val amplio = QualityRanges(phMax = 7.2f, condMax = 7.0f)
        assertEquals(listOf("temperatura"), alerts(25f, 7.15f, 6.8f, 60f, amplio).map { it.type })
    }
}
