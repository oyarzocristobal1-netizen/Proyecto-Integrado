package com.example.myapplication.sensor

import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DemoReadingGeneratorTest {

    @Test
    fun todasLasLecturasDemoSonSimuladasYValidas() {
        val generator = DemoReadingGenerator(Random(42))
        val now = 1_794_600_000_000L
        DemoScenario.entries.forEach { scenario ->
            repeat(200) { i ->
                val r = generator.next(scenario, now + i)
                assertEquals(ReadingSource.SIMULATED, r.source)
                assertTrue(r.isSimulated)
                assertEquals(now + i, r.timestamp)
                assertTrue(MeasurementValidator.validate(r, now + i).isValid)
                if (scenario == DemoScenario.VARIED) return@repeat // sus valores se sortean (ver prueba aparte)
                // Ruido pequeño alrededor del valor base de la muestra (mas el redondeo)
                assertTrue(abs(r.temperature - scenario.baseTemp) <= 0.25f)
                assertTrue(abs(r.ph - scenario.basePh) <= 0.025f)
                assertTrue(abs(r.conductivity - scenario.baseCond) <= 0.06f)
                assertTrue(abs(r.turbidity - scenario.baseTurb) <= 0.55f)
            }
        }
    }

    @Test
    fun conLaMismaSemillaSeRepiten() {
        val a = DemoReadingGenerator(Random(7)).next(DemoScenario.NORMAL, 1L)
        val b = DemoReadingGenerator(Random(7)).next(DemoScenario.NORMAL, 1L)
        assertEquals(a, b)
    }

    @Test
    fun laMuestraVariadaCambiaEnCadaMedicionYSiempreEsValida() {
        val generator = DemoReadingGenerator(Random(3))
        val now = 1_794_600_000_000L
        val samples = List(400) { generator.sampleFor(DemoScenario.VARIED) }
        // Hay variedad real en los cuatro parametros
        assertTrue(samples.map { it.temperature.toInt() }.toSet().size >= 5)
        assertTrue(samples.map { (it.ph * 20).toInt() }.toSet().size >= 5)
        assertTrue(samples.map { it.conductivity.toInt() }.toSet().size >= 4)
        assertTrue(samples.map { it.turbidity.toInt() / 10 }.toSet().size >= 4)
        // y todas las lecturas son simuladas y pasan la validacion del sensor
        samples.forEachIndexed { i, sample ->
            val r = generator.next(DemoScenario.VARIED, now + i, sample)
            assertTrue(r.isSimulated)
            assertTrue(MeasurementValidator.validate(r, now + i).isValid)
        }
    }

    @Test
    fun laMuestraVariadaIncluyeBuenasYAlteradas() {
        val generator = DemoReadingGenerator(Random(11))
        val priorities = List(600) {
            val reading = generator.next(DemoScenario.VARIED, 1_000L + it)
            com.example.myapplication.model.analysis.MilkAnalyzer.analyze(
                com.example.myapplication.model.analysis.AnalysisReading(
                    reading.timestamp, reading.temperature, reading.ph, reading.conductivity, reading.turbidity, reading.source, "lote"
                )
            ).priority
        }
        val normal = priorities.count { it == com.example.myapplication.model.analysis.PriorityLevel.NORMAL }
        assertTrue("normales: $normal", normal in 200..400)
        assertTrue(priorities.any { it == com.example.myapplication.model.analysis.PriorityLevel.PREVENTIVE })
        assertTrue(priorities.any { it == com.example.myapplication.model.analysis.PriorityLevel.WARNING })
        assertTrue(priorities.any { it == com.example.myapplication.model.analysis.PriorityLevel.CRITICAL })
    }

    @Test
    fun variasLecturasDeUnaMismaMuestraSonParecidas() {
        val generator = DemoReadingGenerator(Random(5))
        val sample = generator.sampleFor(DemoScenario.VARIED)
        val readings = List(20) { generator.next(DemoScenario.VARIED, 1_000L + it, sample) }
        assertTrue(readings.all { abs(it.temperature - sample.temperature) <= 0.25f })
        assertTrue(readings.all { abs(it.ph - sample.ph) <= 0.025f })
    }
}
