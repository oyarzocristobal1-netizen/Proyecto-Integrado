package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionRulesTest {

    @Test
    fun soloElAdministradorPuedeEliminar() {
        assertTrue(Role.ADMIN.canDeleteRecords)
        assertFalse(Role.ENCARGADO.canDeleteRecords)
        assertFalse(Role.LECHERO.canDeleteRecords)
    }

    @Test
    fun unaMedicionTieneLasAlertasDeLosCuatroParametros() {
        assertEquals(
            listOf("m1-temperatura", "m1-ph", "m1-conductividad", "m1-turbidez"),
            DeletionRules.alertIdsFor("m1")
        )
    }

    @Test
    fun losTiposDeAlertaCoincidenConLosQueGeneraLaApp() {
        // Si se agrega un tipo de alerta nuevo hay que sumarlo aqui, o quedarian alertas huerfanas al eliminar.
        val generated = detectAlerts(
            MilkQualityReading(
                timestamp = 1L, temperature = 9f, ph = 8f, conductivity = 9f, turbidity = 10f,
                source = ReadingSource.SIMULATED
            ),
            QualityRanges()
        ).map { it.type }.toSet()
        assertEquals(DeletionRules.ALERT_TYPES.toSet(), generated)
    }

    @Test
    fun lasEscriturasSeRepartenEnGruposQueCabenEnFirestore() {
        val groups = DeletionRules.chunk((1..1000).toList())
        assertEquals(listOf(400, 400, 200), groups.map { it.size })
        assertEquals((1..1000).toList(), groups.flatten())
        assertTrue(groups.all { it.size < 500 })
    }

    @Test
    fun sinDocumentosNoHayGrupos() {
        assertTrue(DeletionRules.chunk(emptyList<Int>()).isEmpty())
        assertEquals(listOf(listOf(1)), DeletionRules.chunk(listOf(1)))
    }

    @Test
    fun descripcionDeLaBitacoraNuncaVaciaNiMasLargaQueLasReglas() {
        assertEquals("(sin descripción)", DeletionRules.logDescription("   "))
        assertEquals("Lote A con 3 mediciones", DeletionRules.logDescription("  Lote   A\ncon 3   mediciones "))
        val long = DeletionRules.logDescription("x".repeat(500))
        assertEquals(300, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals(200, DeletionRules.logId("y".repeat(300)).length)
        assertEquals("(sin id)", DeletionRules.logId(" "))
    }

    @Test
    fun tiposDeEliminacionCoincidenConLasReglasDeFirestore() {
        assertEquals(
            setOf("usuario", "dispositivo", "estanque", "lote", "medicion", "alerta", "calibracion"),
            DeletionType.entries.map { it.id }.toSet()
        )
    }
}
