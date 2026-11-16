package com.example.myapplication.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityEvaluationTest {

    private val ref = QualityRanges()

    @After
    fun restaurarRangos() {
        QualityRangesHolder.current = QualityRanges()
    }

    @Test
    fun lecheNormalRefrigeradaEsExcelente() {
        val a = evaluateQuality(ph = 6.7f, conductivity = 4.8f, turbidity = 85f, temperature = 3.8f, ranges = ref)
        assertEquals(QualityLevel.EXCELLENT, a.level)
        assertTrue(a.recommendations.first().startsWith("Apta para almacenamiento"))
    }

    @Test
    fun lecheBuenaPeroTibiaPideEnfriar() {
        val a = evaluateQuality(6.7f, 4.8f, 85f, 22f, ref)
        assertEquals(QualityLevel.EXCELLENT, a.level)
        assertTrue(a.recommendations.first().contains("enfriarla"))
    }

    @Test
    fun temperaturaSobre25EsPrecaucion() {
        val a = evaluateQuality(6.7f, 4.8f, 85f, 30f, ref)
        assertEquals(QualityLevel.WARNING, a.level)
        assertEquals("Precaución / Temperatura alta", a.title)
    }

    @Test
    fun phLevementeAcidoEsPrecaucion() {
        val a = evaluateQuality(6.5f, 4.8f, 85f, 3.8f, ref)
        assertEquals(QualityLevel.WARNING, a.level)
        assertEquals("Precaución / Ligeramente Ácida", a.title)
    }

    @Test
    fun phMuyAcidoSeRechaza() {
        val a = evaluateQuality(6.25f, 5.8f, 75f, 24.5f, ref)
        assertEquals(QualityLevel.REJECTED, a.level)
        assertTrue(a.summary.contains("pH severamente bajo"))
    }

    @Test
    fun muestraMastiticaSeRechaza() {
        val a = evaluateQuality(7.15f, 6.8f, 60f, 25f, ref)
        assertEquals(QualityLevel.REJECTED, a.level)
        assertTrue(a.summary.contains("pH alcalino"))
        assertTrue(a.summary.contains("Conductividad elevada"))
    }

    @Test
    fun conductividadAltaConPhNormalEsAceptable() {
        val a = evaluateQuality(6.7f, 6.0f, 85f, 3.8f, ref)
        assertEquals(QualityLevel.ACCEPTABLE, a.level)
    }

    @Test
    fun usaLosRangosConfigurados() {
        val amplio = QualityRanges(phMax = 7.2f, condMax = 7.0f, turbMin = 50f)
        val a = evaluateQuality(7.15f, 6.8f, 60f, 3.8f, amplio)
        assertEquals(QualityLevel.EXCELLENT, a.level)
    }

    @Test
    fun estadosDeLaLecturaUsanLosRangosVigentes() {
        val r = MilkQualityReading(temperature = 22f, ph = 7.15f, conductivity = 4.8f, turbidity = 85f)
        assertEquals(PhStatus.ALKALINE, r.phStatus)
        assertEquals(TemperatureStatus.AMBIENT, r.temperatureStatus)
        QualityRangesHolder.current = QualityRanges(phMax = 7.2f, tempMax = 20f)
        assertEquals(PhStatus.NORMAL, r.phStatus)
        assertEquals(TemperatureStatus.AMBIENT, r.temperatureStatus)
        assertEquals(TemperatureStatus.HIGH, r.copy(temperature = 26f).temperatureStatus)
        assertEquals(TemperatureStatus.COLD, r.copy(temperature = 4f).temperatureStatus)
    }

    @Test
    fun validacionDeRangos() {
        assertNull(QualityRanges().validate())
        assertTrue(QualityRanges(phMin = 6.9f, phMax = 6.8f).validate()!!.contains("pH mínimo"))
        assertTrue(QualityRanges(condMin = -1f).validate()!!.contains("negativa"))
        assertTrue(QualityRanges(condMin = 6f, condMax = 5f).validate()!!.contains("conductividad mínima"))
        assertTrue(QualityRanges(turbMin = 120f).validate()!!.contains("turbidez"))
        // Los mismos limites que exigen las reglas de Firestore
        assertTrue(QualityRanges(condMax = 25f).validate()!!.contains("20 mS/cm"))
        assertTrue(QualityRanges(tempMax = 25f).validate()!!.contains("temperatura"))
    }

    @Test
    fun semaforoDelInforme() {
        assertEquals("verde", RecordRules.statusFor(QualityLevel.EXCELLENT))
        assertEquals("ambar", RecordRules.statusFor(QualityLevel.ACCEPTABLE))
        assertEquals("ambar", RecordRules.statusFor(QualityLevel.WARNING))
        assertEquals("rojo", RecordRules.statusFor(QualityLevel.REJECTED))
    }
}
