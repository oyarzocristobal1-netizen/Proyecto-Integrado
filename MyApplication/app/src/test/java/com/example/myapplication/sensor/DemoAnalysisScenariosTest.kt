package com.example.myapplication.sensor

import com.example.myapplication.model.analysis.AnalysisScenario
import com.example.myapplication.model.analysis.MilkAnalyzer
import com.example.myapplication.model.analysis.PriorityLevel
import com.example.myapplication.model.analysis.toAnalysisReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Cada muestra DEMO provoca el escenario de analisis para el que se diseno, con cualquier ruido del simulador. */
class DemoAnalysisScenariosTest {

    private fun expected(scenario: DemoScenario): Pair<AnalysisScenario, PriorityLevel> = when (scenario) {
        DemoScenario.NORMAL -> AnalysisScenario.NORMAL to PriorityLevel.NORMAL
        DemoScenario.ONLY_PH -> AnalysisScenario.SINGLE to PriorityLevel.WARNING
        DemoScenario.PH_AND_CONDUCTIVITY -> AnalysisScenario.TWO to PriorityLevel.WARNING
        DemoScenario.TEMPERATURE_AND_TURBIDITY -> AnalysisScenario.TWO to PriorityLevel.WARNING
        DemoScenario.THREE_ALTERED -> AnalysisScenario.THREE to PriorityLevel.WARNING
        DemoScenario.FOUR_ALTERED -> AnalysisScenario.FOUR to PriorityLevel.CRITICAL
        DemoScenario.MILD_DEVIATIONS -> AnalysisScenario.MILD to PriorityLevel.PREVENTIVE
        DemoScenario.SUSPICIOUS -> AnalysisScenario.SUSPECT to PriorityLevel.PREVENTIVE
        // Muestras anteriores del simulador: solo se comprueba que el analisis las interpreta sin errores.
        else -> AnalysisScenario.NORMAL to PriorityLevel.NORMAL
    }

    @Test
    fun cadaMuestraDemoProduceElEscenarioEsperado() {
        val generator = DemoReadingGenerator(Random(11))
        val specific = listOf(
            DemoScenario.NORMAL, DemoScenario.ONLY_PH, DemoScenario.PH_AND_CONDUCTIVITY,
            DemoScenario.TEMPERATURE_AND_TURBIDITY, DemoScenario.THREE_ALTERED, DemoScenario.FOUR_ALTERED,
            DemoScenario.MILD_DEVIATIONS, DemoScenario.SUSPICIOUS
        )
        specific.forEach { scenario ->
            repeat(300) { i ->
                val analysis = MilkAnalyzer.analyze(generator.next(scenario, 1_000L + i).toAnalysisReading("lote"))
                val (scene, priority) = expected(scenario)
                assertEquals("${scenario.label} #$i", scene, analysis.scenario)
                assertEquals("${scenario.label} #$i", priority, analysis.priority)
                assertTrue(analysis.simulated)
            }
        }
    }

    @Test
    fun lasMuestrasAnterioresDelSimuladorSeInterpretanSinErrores() {
        val generator = DemoReadingGenerator(Random(5))
        listOf(DemoScenario.ACIDIC_BACTERIAL, DemoScenario.ALKALINE_MASTITIS, DemoScenario.DILUTED_WATER).forEach { scenario ->
            val analysis = MilkAnalyzer.analyze(generator.next(scenario, 1_000L).toAnalysisReading("lote"))
            assertTrue(scenario.label, analysis.altered.isNotEmpty())
            assertTrue(scenario.label, analysis.parameterCards.isNotEmpty())
            assertTrue(scenario.label, analysis.actions.isNotEmpty())
        }
    }
}
