package com.example.myapplication.sensor

import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFrameParserTest {

    private val now = 1_794_600_000_000L
    private fun parser() = SensorFrameParser { now }

    private fun readingOf(result: FrameResult) = (result as FrameResult.Reading).reading

    @Test
    fun formatoDataCompleto() {
        val r = readingOf(parser().parse("DATA:4.1,6.70,4.80,85.0"))
        assertEquals(4.1f, r.temperature, 0.0001f)
        assertEquals(6.70f, r.ph, 0.0001f)
        assertEquals(4.80f, r.conductivity, 0.0001f)
        assertEquals(85.0f, r.turbidity, 0.0001f)
        assertEquals(ReadingSource.DEVICE, r.source)
        assertEquals(now, r.timestamp)
    }

    @Test
    fun aceptaEspaciosYRetornoDeCarro() {
        assertTrue(parser().parse("  DATA:4.1, 6.70 ,4.80,85.0\r") is FrameResult.Reading)
    }

    @Test
    fun descartaTramaConValoresFaltantes() {
        val result = parser().parse("DATA:4.1,6.70,4.80")
        assertTrue(result is FrameResult.Invalid)
        assertTrue((result as FrameResult.Invalid).reason.contains("4 valores"))
    }

    @Test
    fun descartaValorNoNumerico() {
        assertTrue(parser().parse("DATA:4.1,abc,4.80,85.0") is FrameResult.Invalid)
        assertTrue(parser().parse("DATA:NaN,6.7,4.8,85") is FrameResult.Invalid)
        assertTrue(parser().parse("DATA:4.1,,4.80,85.0") is FrameResult.Invalid)
    }

    @Test
    fun descartaComaDecimal() {
        // "6,70" partiria la trama en 5 valores
        assertTrue(parser().parse("DATA:4.1,6,70,4.80,85.0") is FrameResult.Invalid)
    }

    @Test
    fun formatoClaveValorNoConfundeTurbidezConTemperatura() {
        val r = readingOf(parser().parse("T:4.1,PH:6.7,CE:4.8,TURB:85"))
        assertEquals(4.1f, r.temperature, 0.0001f)
        assertEquals(85f, r.turbidity, 0.0001f)
    }

    @Test
    fun formatoClaveValorEnCualquierOrden() {
        val r = readingOf(parser().parse("TURB:85,CE:4.8,PH:6.7,T:4.1"))
        assertEquals(4.1f, r.temperature, 0.0001f)
        assertEquals(6.7f, r.ph, 0.0001f)
    }

    @Test
    fun claveValorIncompletoORepetidoSeDescarta() {
        val faltante = parser().parse("T:4.1,PH:6.7,CE:4.8")
        assertTrue((faltante as FrameResult.Invalid).reason.contains("turbidez"))
        assertTrue(parser().parse("T:4.1,PH:6.7,PH:6.8,TURB:85") is FrameResult.Invalid)
        assertTrue(parser().parse("T:4.1,PH:6.7,XX:1,TURB:85") is FrameResult.Invalid)
    }

    @Test
    fun cicloDeCuatroLineas() {
        val p = parser()
        assertEquals(FrameResult.Partial, p.parse("Temperatura: 4.1 C"))
        assertEquals(FrameResult.Partial, p.parse("pH: 6.70"))
        assertEquals(FrameResult.Partial, p.parse("Conductividad: 4.80 mS/cm"))
        val r = readingOf(p.parse("Turbidez: 85.0 %"))
        assertEquals(4.8f, r.conductivity, 0.0001f)
        // El siguiente ciclo empieza de cero
        assertEquals(FrameResult.Partial, p.parse("Temperatura: 4.2 C"))
    }

    @Test
    fun cicloIncompletoSeDescartaSinInventarValores() {
        val p = parser()
        p.parse("Temperatura: 4.1 C")
        p.parse("pH: 6.70")
        val result = p.parse("Temperatura: 4.3 C")
        assertTrue((result as FrameResult.Invalid).reason.contains("Ciclo incompleto"))
        // Con el ciclo reiniciado faltan aun 3 valores
        assertEquals(FrameResult.Partial, p.parse("pH: 6.71"))
        assertEquals(FrameResult.Partial, p.parse("Conductividad: 4.8"))
        assertTrue(p.parse("Turbidez: 84") is FrameResult.Reading)
    }

    @Test
    fun lineasQueNoSonDatosSeIgnoran() {
        val p = parser()
        assertEquals(FrameResult.Ignored, p.parse(""))
        assertEquals(FrameResult.Ignored, p.parse("Iniciando sensores..."))
        assertEquals(FrameResult.Ignored, p.parse("Estado: listo"))
    }

    @Test
    fun sensorDesconectadoPasaElParserPeroLoRechazaElValidador() {
        val r = readingOf(parser().parse("DATA:-127.0,6.70,4.80,85.0"))
        assertFalse(MeasurementValidator.validate(r, now).isValid)
    }
}
