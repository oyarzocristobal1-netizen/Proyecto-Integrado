package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class RecordRulesTest {

    // 13/11/2026 20:00 UTC = 13/11/2026 17:00 en Chile (UTC-3)
    private val fecha = Date(1_794_600_000_000L)
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun idDeLoteSinTildesNiEspacios() {
        assertEquals("20261113-estanque-1", RecordRules.lotIdFor("Estanque 1", fecha, utc))
        assertEquals("20261113-estanque-nuble-2", RecordRules.lotIdFor("  Estanque Ñuble #2 ", fecha, utc))
    }

    @Test
    fun mismoEstanqueYDiaDaElMismoLote() {
        assertEquals(RecordRules.lotIdFor("ESTANQUE 1", fecha, utc), RecordRules.lotIdFor("estanque-1", fecha, utc))
    }

    @Test
    fun nombreSinLetrasUsaEstanque() {
        assertEquals("20261113-estanque", RecordRules.lotIdFor("###", fecha, utc))
    }

    @Test
    fun laFechaDelLoteUsaLaZonaHoraria() {
        // 13/11/2026 23:30 en Chile (UTC-3) = 14/11/2026 02:30 UTC
        val noche = Date(1_794_623_400_000L)
        assertEquals("20261114-e1", RecordRules.lotIdFor("E1", noche, utc))
        assertEquals("20261113-e1", RecordRules.lotIdFor("E1", noche, TimeZone.getTimeZone("America/Santiago")))
    }

    @Test
    fun dispositivoSegunOrigen() {
        assertEquals("caja-01", RecordRules.deviceIdFor(ReadingSource.DEVICE))
        assertEquals("simulador", RecordRules.deviceIdFor(ReadingSource.SIMULATED))
    }

    @Test
    fun origenDesconocidoSeTrataComoSimulado() {
        assertEquals(ReadingSource.SIMULATED, ReadingSource.fromId(null))
        assertEquals(ReadingSource.SIMULATED, ReadingSource.fromId("otro"))
        assertEquals(ReadingSource.DEVICE, ReadingSource.fromId("sensor"))
        assertEquals(ReadingSource.SIMULATED, MilkQualityReading().source)
    }
}
