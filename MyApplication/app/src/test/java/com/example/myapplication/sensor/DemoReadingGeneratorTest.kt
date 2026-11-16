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
}
