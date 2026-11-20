package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingTextTest {

    private fun finding(p: Parameter, t: Float = 3.5f, ph: Float = 6.7f, cond: Float = 4.8f, turb: Float = 85f): ParameterFinding {
        val a = MilkAnalyzer.analyze(
            AnalysisReading(1_000L, t, ph, cond, turb, ReadingSource.SIMULATED, "lote"),
            emptyList(), QualityRanges()
        )
        return a.findings.first { it.parameter == p }
    }

    @Test
    fun dentroDelRango() {
        Parameter.entries.forEach { assertEquals("Dentro del rango", finding(it).statusText) }
    }

    @Test
    fun concordanciaDeGeneroEnPhYElResto() {
        assertEquals("Leve · alta", finding(Parameter.TEMPERATURE, t = 5.0f).statusText)
        assertEquals("Moderada · alto", finding(Parameter.PH, ph = 7.0f).statusText)
        assertEquals("Muy alta", finding(Parameter.CONDUCTIVITY, cond = 6.5f).statusText)
        assertEquals("Moderada · baja", finding(Parameter.TURBIDITY, turb = 38f).statusText)
        assertEquals("Muy baja", finding(Parameter.TURBIDITY, turb = 25f).statusText)
    }

    @Test
    fun criticaYDudosa() {
        assertEquals("Crítica · alta", finding(Parameter.TEMPERATURE, t = 27f).statusText)
        assertEquals("Lectura dudosa", finding(Parameter.CONDUCTIVITY, cond = 0.1f).statusText)
    }

    @Test
    fun losTextosCabenEnUnRecuadroDeMedioAncho() {
        // El recuadro de un sensor muestra el estado en una sola linea: no debe pasar de ~18 caracteres.
        val all = listOf(
            finding(Parameter.TEMPERATURE, t = 5.0f), finding(Parameter.PH, ph = 7.0f),
            finding(Parameter.CONDUCTIVITY, cond = 6.5f), finding(Parameter.TURBIDITY, turb = 25f),
            finding(Parameter.TEMPERATURE, t = 27f), finding(Parameter.CONDUCTIVITY, cond = 0.1f)
        )
        all.forEach { assertTrue(it.statusText, it.statusText.length <= 18) }
    }
}
