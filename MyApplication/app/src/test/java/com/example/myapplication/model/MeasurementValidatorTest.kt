package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementValidatorTest {

    private val now = 1_794_600_000_000L

    private fun reading(
        t: Float = 3.8f, ph: Float = 6.7f, ce: Float = 4.8f, tu: Float = 85f, ts: Long = now
    ) = MilkQualityReading(t, ph, ce, tu, ts)

    @Test
    fun lecturaCorrectaEsValida() {
        val result = MeasurementValidator.validate(reading(), now)
        assertTrue(result.isValid)
        assertNull(result.firstError)
    }

    @Test
    fun rechazaValorNoNumerico() {
        val result = MeasurementValidator.validate(reading(ph = Float.NaN), now)
        assertFalse(result.isValid)
        assertTrue(result.firstError!!.startsWith("pH sin valor numérico"))
    }

    @Test
    fun rechazaInfinito() {
        val result = MeasurementValidator.validate(reading(ce = Float.POSITIVE_INFINITY), now)
        assertFalse(result.isValid)
    }

    @Test
    fun rechazaSensorDeTemperaturaDesconectado() {
        // El DS18B20 desconectado entrega -127 °C
        val result = MeasurementValidator.validate(reading(t = -127f), now)
        assertFalse(result.isValid)
        assertTrue(result.firstError!!.startsWith("Temperatura fuera de los límites"))
    }

    @Test
    fun rechazaValoresFueraDeLimitesFisicos() {
        assertFalse(MeasurementValidator.validate(reading(ph = 14.5f), now).isValid)
        assertFalse(MeasurementValidator.validate(reading(ce = -0.1f), now).isValid)
        assertFalse(MeasurementValidator.validate(reading(tu = 120f), now).isValid)
    }

    @Test
    fun aceptaLosBordesDeLosLimites() {
        assertTrue(MeasurementValidator.validate(reading(t = -5f, ph = 0f, ce = 0f, tu = 0f), now).isValid)
        assertTrue(MeasurementValidator.validate(reading(t = 80f, ph = 14f, ce = 20f, tu = 100f), now).isValid)
    }

    @Test
    fun rechazaLecturaAntigua() {
        val result = MeasurementValidator.validate(reading(ts = now - 20_000), now)
        assertFalse(result.isValid)
        assertTrue(result.firstError!!.contains("más de 15 s"))
    }

    @Test
    fun aceptaLecturaReciente() {
        assertTrue(MeasurementValidator.validate(reading(ts = now - 14_000), now).isValid)
    }

    @Test
    fun rechazaLecturaConHoraFutura() {
        val result = MeasurementValidator.validate(reading(ts = now + 120_000), now)
        assertFalse(result.isValid)
        assertTrue(result.firstError!!.contains("hora futura"))
    }

    @Test
    fun rechazaLecturaYaRegistrada() {
        val r = reading()
        val result = MeasurementValidator.validate(r, now, lastRecordedTimestamp = r.timestamp)
        assertFalse(result.isValid)
        assertTrue(result.firstError!!.contains("ya fue registrada"))
    }

    @Test
    fun reportaTodosLosErrores() {
        val result = MeasurementValidator.validate(reading(t = -127f, ph = Float.NaN, ts = now - 60_000), now)
        assertEquals(3, result.errors.size)
    }

    @Test
    fun validaNombreDeEstanque() {
        assertNull(MeasurementValidator.validateTankName("Estanque 1"))
        assertNull(MeasurementValidator.validateTankName("  Estanque Ñuble #2  "))
        assertNull(MeasurementValidator.validateTankName("Est. 3/B - 4°C"))
        assertTrue(MeasurementValidator.validateTankName("   ")!!.startsWith("Escribe el nombre"))
        assertTrue(MeasurementValidator.validateTankName("x".repeat(41))!!.contains("40 caracteres"))
        assertTrue(MeasurementValidator.validateTankName("Estanque<script>")!!.startsWith("Usa solo"))
    }
}
