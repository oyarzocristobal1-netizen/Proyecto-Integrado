package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityTrackerTest {

    private fun r(ph: Float, t: Float = 3.8f, source: ReadingSource = ReadingSource.SIMULATED) =
        MilkQualityReading(temperature = t, ph = ph, conductivity = 4.8f, turbidity = 85f, source = source)

    @Test
    fun necesitaCuatroLecturas() {
        val tracker = StabilityTracker()
        val s1 = tracker.add(r(6.70f))
        tracker.add(r(6.71f))
        val s3 = tracker.add(r(6.70f))
        assertFalse(s1.isStable)
        assertEquals(1, s1.samples)
        assertFalse(s3.isStable)
        assertEquals(3, s3.samples)
        assertEquals(4, s3.required)
        assertTrue(tracker.add(r(6.72f)).isStable)
    }

    @Test
    fun noEsEstableSiElPhVariaMucho() {
        val tracker = StabilityTracker()
        listOf(6.70f, 6.71f, 6.70f).forEach { tracker.add(r(it)) }
        assertFalse(tracker.add(r(7.10f)).isStable)
    }

    @Test
    fun seEstabilizaDespuesDeCambiarDeMuestra() {
        val tracker = StabilityTracker()
        repeat(4) { tracker.add(r(6.70f)) }
        assertFalse(tracker.add(r(7.15f)).isStable)
        tracker.add(r(7.14f))
        tracker.add(r(7.16f))
        assertTrue(tracker.add(r(7.15f)).isStable)
    }

    @Test
    fun noEsEstableSiLaTemperaturaVaria() {
        val tracker = StabilityTracker()
        listOf(3.8f, 3.9f, 3.8f).forEach { tracker.add(r(6.7f, t = it)) }
        assertFalse(tracker.add(r(6.7f, t = 5.0f)).isStable)
    }

    @Test
    fun cambiarDeOrigenReiniciaElConteo() {
        val tracker = StabilityTracker()
        repeat(4) { tracker.add(r(6.70f)) }
        val s = tracker.add(r(6.70f, source = ReadingSource.DEVICE))
        assertFalse(s.isStable)
        assertEquals(1, s.samples)
    }
}
