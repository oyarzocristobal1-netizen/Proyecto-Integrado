package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val BASE = 10 * 3_600_000L
private const val MINUTE = 60_000L

private fun at(
    minutes: Int, t: Float = 3.5f, ph: Float = 6.7f, cond: Float = 4.8f, turb: Float = 85f,
    lot: String? = "lote-1", source: ReadingSource = ReadingSource.SIMULATED
) = AnalysisReading(BASE + minutes * MINUTE, t, ph, cond, turb, source, lot)

class ForecastTest {

    private val ranges = QualityRanges()
    private val config = AnalysisConfig()

    private fun forecast(current: AnalysisReading, previous: List<AnalysisReading>, c: AnalysisConfig = config) =
        ForecastEngine.forecast(current, previous, ranges, c)

    private fun ForecastResult.of(p: Parameter) = forecasts.first { it.parameter == p }

    // ------------------------------------------------------------------ datos insuficientes

    @Test
    fun sinHistorialNoSeProyecta() {
        val result = forecast(at(0), emptyList())
        assertTrue(result.forecasts.all { it.status == ForecastStatus.INSUFFICIENT_DATA })
        assertNotNull(result.note)
        assertTrue(result.notable.isEmpty())
        assertNull(result.soonestExit)
    }

    @Test
    fun conSoloDosMuestrasNoSeProyecta() {
        val result = forecast(at(30, cond = 5.0f), listOf(at(0, cond = 4.6f)))
        assertTrue(result.forecasts.all { it.status == ForecastStatus.INSUFFICIENT_DATA })
        assertTrue(result.note!!.contains("al menos"))
    }

    @Test
    fun medicionesDeLaMismaMuestraNoCuentanComoVarias() {
        // Cuatro lecturas en 6 minutos son una sola muestra.
        val previous = listOf(at(0, cond = 4.6f), at(2, cond = 4.7f), at(4, cond = 4.8f))
        val result = forecast(at(6, cond = 4.9f), previous)
        assertTrue(result.forecasts.all { it.status == ForecastStatus.INSUFFICIENT_DATA })
    }

    @Test
    fun siLasMuestrasEstanMuyJuntasEnElTiempoNoSeProyecta() {
        val previous = listOf(at(0, cond = 4.6f), at(11, cond = 4.8f))
        val strict = config.copy(forecastMinSpanMinutes = 60)
        val result = forecast(at(22, cond = 5.0f), previous, strict)
        assertTrue(result.forecasts.all { it.status == ForecastStatus.INSUFFICIENT_DATA })
        assertTrue(result.note!!.contains("min"))
    }

    @Test
    fun medicionesDeOtroLoteOFuenteSeIgnoran() {
        val previous = listOf(
            at(0, cond = 4.6f, lot = "otro"), at(30, cond = 4.9f, lot = "otro"),
            at(0, cond = 4.6f, source = ReadingSource.DEVICE), at(30, cond = 4.9f, source = ReadingSource.DEVICE)
        )
        val result = forecast(at(60, cond = 5.2f), previous)
        assertTrue(result.forecasts.all { it.status == ForecastStatus.INSUFFICIENT_DATA })
    }

    // ------------------------------------------------------------------ conductividad (arriba y abajo)

    @Test
    fun conductividadQueSubeAvisaCuandoSaldriaDelRango() {
        val previous = listOf(at(0, cond = 4.6f), at(30, cond = 4.9f))
        val f = forecast(at(60, cond = 5.2f), previous).of(Parameter.CONDUCTIVITY)
        assertEquals(ForecastStatus.WILL_EXIT_RANGE, f.status)
        assertEquals(30, f.minutesToEvent)
        assertTrue(f.headline.contains("saldría del rango"))
        assertTrue(f.detail.contains("no diagnostica"))
        assertEquals(3, f.samples)
    }

    @Test
    fun conductividadQueBajaTambienSeProyecta() {
        val previous = listOf(at(0, cond = 4.8f), at(30, cond = 4.5f))
        val f = forecast(at(60, cond = 4.2f), previous).of(Parameter.CONDUCTIVITY)
        assertEquals(ForecastStatus.WILL_EXIT_RANGE, f.status)
        assertEquals(20, f.minutesToEvent)
    }

    @Test
    fun conductividadFueraDeRangoQueSigueSubiendoEmpeora() {
        val previous = listOf(at(0, cond = 5.6f), at(30, cond = 5.9f))
        val f = forecast(at(60, cond = 6.2f), previous).of(Parameter.CONDUCTIVITY)
        assertEquals(ForecastStatus.WORSENING, f.status)
        assertTrue(f.detail.contains("no diagnostica"))
    }

    @Test
    fun conductividadAltaQueBajaVuelveAlRango() {
        val previous = listOf(at(0, cond = 6.4f), at(30, cond = 6.1f))
        val f = forecast(at(60, cond = 5.8f), previous).of(Parameter.CONDUCTIVITY)
        assertEquals(ForecastStatus.IMPROVING, f.status)
        assertEquals(30, f.minutesToEvent)
    }

    @Test
    fun cambioLentoQueNoSaldriaDelRangoEnElHorizonteEsEstable() {
        // +0,2 mS/cm por hora: llegar a 5,5 tardaria mas de 3 h, mas que el horizonte de 2 h.
        val previous = listOf(at(0, cond = 4.4f), at(60, cond = 4.6f))
        val f = forecast(at(120, cond = 4.8f), previous).of(Parameter.CONDUCTIVITY)
        assertEquals(ForecastStatus.STABLE, f.status)
        assertNull(f.minutesToEvent)
    }

    // ------------------------------------------------------------------ temperatura, pH, turbidez

    @Test
    fun temperaturaQueEnfriaEstimaCuandoLlegaAlLimite() {
        val previous = listOf(at(0, t = 9f), at(30, t = 7.5f))
        val f = forecast(at(60, t = 6f), previous).of(Parameter.TEMPERATURE)
        assertEquals(ForecastStatus.IMPROVING, f.status)
        assertEquals(40, f.minutesToEvent)
        assertTrue(f.detail.contains("estimación lineal"))
    }

    @Test
    fun temperaturaQueSubeFueraDeRangoEmpeora() {
        val previous = listOf(at(0, t = 5f), at(30, t = 6f))
        assertEquals(ForecastStatus.WORSENING, forecast(at(60, t = 7f), previous).of(Parameter.TEMPERATURE).status)
    }

    @Test
    fun temperaturaFueraDeRangoSinCambiosNoMejora() {
        val previous = listOf(at(0, t = 6.0f), at(30, t = 6.1f))
        assertEquals(ForecastStatus.NOT_IMPROVING, forecast(at(60, t = 6.0f), previous).of(Parameter.TEMPERATURE).status)
    }

    @Test
    fun temperaturaQueSubeDentroDelRangoSaldriaDeEl() {
        val previous = listOf(at(0, t = 2.0f), at(30, t = 2.8f))
        val f = forecast(at(60, t = 3.5f), previous).of(Parameter.TEMPERATURE)
        assertEquals(ForecastStatus.WILL_EXIT_RANGE, f.status)
        assertTrue(f.minutesToEvent!! in 15..45)
    }

    @Test
    fun phQueBajaSaldriaPorElLimiteInferior() {
        val previous = listOf(at(0, ph = 6.78f), at(30, ph = 6.74f))
        val f = forecast(at(60, ph = 6.70f), previous).of(Parameter.PH)
        assertEquals(ForecastStatus.WILL_EXIT_RANGE, f.status)
        assertEquals(75, f.minutesToEvent)
    }

    @Test
    fun turbidezQueBajaSaldriaPorElMinimo() {
        val previous = listOf(at(0, turb = 80f), at(30, turb = 70f))
        val f = forecast(at(60, turb = 60f), previous).of(Parameter.TURBIDITY)
        assertEquals(ForecastStatus.WILL_EXIT_RANGE, f.status)
        assertEquals(30, f.minutesToEvent)
        assertTrue(!f.detail.contains("viscos"))
    }

    @Test
    fun todoEstableNoTieneNada_notable() {
        val previous = listOf(at(0), at(30, t = 3.6f), at(60, t = 3.5f))
        val result = forecast(at(90), previous)
        assertTrue(result.forecasts.all { it.status == ForecastStatus.STABLE })
        assertTrue(result.notable.isEmpty())
    }

    // ------------------------------------------------------------------ matematica y formato

    @Test
    fun pendienteDeUnaRectaExacta() {
        val samples = listOf(at(0, cond = 4.0f), at(30, cond = 4.5f), at(60, cond = 5.0f))
        assertTrue(abs(ForecastEngine.slopePerHour(Parameter.CONDUCTIVITY, samples) - 1.0) < 1e-6)
        assertEquals(0.0, ForecastEngine.slopePerHour(Parameter.PH, samples), 1e-9)
    }

    @Test
    fun formatoDeMinutos() {
        assertEquals("45 min", ForecastEngine.formatMinutes(45))
        assertEquals("2 h", ForecastEngine.formatMinutes(120))
        assertEquals("1 h 20 min", ForecastEngine.formatMinutes(80))
    }

    // ------------------------------------------------------------------ dentro del analisis completo

    @Test
    fun salidaPrevistaPideSeguimientoPreventivoYRemedirAntes() {
        val previous = listOf(at(0, cond = 4.6f), at(30, cond = 4.9f))
        val analysis = MilkAnalyzer.analyze(at(60, cond = 5.2f), previous, ranges, config)
        assertEquals(PriorityLevel.PREVENTIVE, analysis.priority)
        assertEquals(Parameter.CONDUCTIVITY, analysis.forecast.soonestExit?.parameter)
        // 30 min hasta el limite: se sugiere remedir a la mitad, 15 min.
        assertEquals(15, analysis.recheckInMinutes)
    }

    @Test
    fun muestraNormalYEstableNoPideRemedir() {
        val previous = listOf(at(0), at(30), at(60))
        val analysis = MilkAnalyzer.analyze(at(90), previous, ranges, config)
        assertEquals(PriorityLevel.NORMAL, analysis.priority)
        assertNull(analysis.recheckInMinutes)
    }

    @Test
    fun lecturaDudosaNoSeProyectaYPideRepetir() {
        val previous = listOf(at(0), at(30))
        val analysis = MilkAnalyzer.analyze(at(60, cond = 0.1f), previous, ranges, config)
        assertTrue(!analysis.reliable)
        assertTrue(analysis.forecast.forecasts.isEmpty())
        assertNotNull(analysis.forecast.note)
        assertEquals(config.recheckSuspectMinutes, analysis.recheckInMinutes)
    }

    @Test
    fun prioridadCriticaSugiereRemedirEnQuinceMinutos() {
        val analysis = MilkAnalyzer.analyze(at(0, t = 27f, ph = 7.3f, cond = 7.5f, turb = 30f), emptyList(), ranges, config)
        assertEquals(PriorityLevel.CRITICAL, analysis.priority)
        assertEquals(config.recheckCriticalMinutes, analysis.recheckInMinutes)
    }
}
