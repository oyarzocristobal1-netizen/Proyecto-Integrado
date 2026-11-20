package com.example.myapplication.model

import com.example.myapplication.model.analysis.VerdictLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SaleReleaseTest {

    @Test
    fun elAnalisisSugiereSegunElVeredicto() {
        assertEquals(SaleDecision.APTO, SaleRelease.suggestion(VerdictLevel.GOOD))
        assertEquals(SaleDecision.NO_APTO, SaleRelease.suggestion(VerdictLevel.STOP))
        assertNull(SaleRelease.suggestion(VerdictLevel.REVIEW))
        assertNull(SaleRelease.suggestion(null))
    }

    @Test
    fun liberarUnLoteBuenoNoPideNota() {
        assertNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.GOOD, "", 3, overdue = false))
    }

    @Test
    fun liberarUnLoteQueElAnalisisMarcoPideExplicacion() {
        assertNotNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.STOP, "", 3, false))
        assertNotNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.STOP, "ab", 3, false))
        assertNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.STOP, "Se repitió la medición", 3, false))
        assertNotNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.REVIEW, " ", 3, false))
        assertNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.REVIEW, "Revisado en terreno", 3, false))
    }

    @Test
    fun medicionAtrasadaPideExplicacionSoloSiSeLibera() {
        assertNotNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.GOOD, "", 3, overdue = true))
        assertNull(SaleRelease.validate(SaleDecision.APTO, VerdictLevel.GOOD, "Se midió a mano", 3, overdue = true))
        assertNull(SaleRelease.validate(SaleDecision.NO_APTO, VerdictLevel.GOOD, "", 3, overdue = true))
    }

    @Test
    fun noLiberarNuncaExigeNota() {
        assertNull(SaleRelease.validate(SaleDecision.NO_APTO, VerdictLevel.STOP, "", 2, false))
        assertNull(SaleRelease.validate(SaleDecision.NO_APTO, VerdictLevel.GOOD, "", 2, false))
    }

    @Test
    fun sinDecisionOSinMedicionesNoSeGuarda() {
        assertNotNull(SaleRelease.validate(null, VerdictLevel.GOOD, "", 3, false))
        assertNotNull(SaleRelease.validate(SaleDecision.APTO, null, "", 0, false))
    }

    @Test
    fun laNotaTieneMaximo() {
        assertNull(SaleRelease.validate(SaleDecision.NO_APTO, VerdictLevel.GOOD, "a".repeat(200), 1, false))
        assertNotNull(SaleRelease.validate(SaleDecision.NO_APTO, VerdictLevel.GOOD, "a".repeat(201), 1, false))
    }

    @Test
    fun idsDeDecisionCoincidenConLasReglasDeFirestore() {
        assertEquals(setOf("apto", "no_apto"), SaleDecision.entries.map { it.id }.toSet())
        assertEquals(SaleDecision.NO_APTO, SaleDecision.fromId("no_apto"))
        assertNull(SaleDecision.fromId("otro"))
    }
}
