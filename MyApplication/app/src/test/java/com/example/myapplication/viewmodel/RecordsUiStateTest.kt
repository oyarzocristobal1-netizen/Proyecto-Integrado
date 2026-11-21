package com.example.myapplication.viewmodel

import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** Filtros del historial y contadores del panel (sin Firebase). */
class RecordsUiStateTest {

    private fun m(id: String, status: String, simulated: Boolean, pending: Boolean = false) = SavedMeasurement(
        id = id,
        dateTime = Date(),
        lotId = "20261113-estanque-1",
        deviceId = if (simulated) "simulador" else "caja-01",
        milkerUid = "uid",
        milkerName = "Ana",
        temperature = 3.8,
        ph = 6.7,
        conductivity = 4.8,
        turbidity = 85.0,
        status = status,
        title = "",
        source = if (simulated) ReadingSource.SIMULATED else ReadingSource.DEVICE,
        pendingSync = pending
    )

    private val history = listOf(
        m("1", "verde", simulated = true),
        m("2", "rojo", simulated = true),
        m("3", "verde", simulated = false),
        m("4", "ambar", simulated = false, pending = true)
    )

    @Test
    fun filtroDeOrigen() {
        assertTrue(OriginFilter.ALL.matches(true) && OriginFilter.ALL.matches(false))
        assertTrue(OriginFilter.SENSOR.matches(false))
        assertFalse(OriginFilter.SENSOR.matches(true))
        assertTrue(OriginFilter.SIMULATED.matches(true))
        assertFalse(OriginFilter.SIMULATED.matches(false))
    }

    @Test
    fun historialFiltradoPorOrigenYSemaforo() {
        val base = RecordsUiState(history = history)
        assertEquals(4, base.filteredHistory.size)
        assertEquals(listOf("3", "4"), base.copy(historyOrigin = OriginFilter.SENSOR).filteredHistory.map { it.id })
        assertEquals(listOf("1", "3"), base.copy(historyStatus = StatusFilter.GREEN).filteredHistory.map { it.id })
        assertEquals(
            listOf("2"),
            base.copy(historyOrigin = OriginFilter.SIMULATED, historyStatus = StatusFilter.RED).filteredHistory.map { it.id }
        )
    }

    @Test
    fun filtroDeOrigenSoloConDatosDeLosDosOrigenes() {
        // Solo simuladas (la caja no existe): el filtro no se muestra.
        assertFalse(RecordsUiState(recent = history.filter { it.isSimulated }).hasMixedOrigins)
        // Solo del sensor: tampoco.
        assertFalse(RecordsUiState(history = history.filter { !it.isSimulated }).hasMixedOrigins)
        // De los dos origenes (en el panel o en el historial): si.
        assertTrue(RecordsUiState(recent = history).hasMixedOrigins)
        assertTrue(RecordsUiState(recent = history.take(1), history = history.drop(2)).hasMixedOrigins)
        assertFalse(RecordsUiState().hasMixedOrigins)
    }

    @Test
    fun contadoresDelPanel() {
        val st = RecordsUiState(recent = history)
        assertEquals(1, st.pendingSyncCount)
        assertEquals(2, st.recentSimulatedCount)
    }
}
