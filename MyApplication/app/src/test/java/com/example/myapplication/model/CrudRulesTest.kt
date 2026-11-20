package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrudRulesTest {

    @Test
    fun motivoDeAnulacionDebeTenerEntre3y200Caracteres() {
        assertNotNull(CrudRules.validateAnnulReason(""))
        assertNotNull(CrudRules.validateAnnulReason("   "))
        assertNotNull(CrudRules.validateAnnulReason("ab"))
        assertNull(CrudRules.validateAnnulReason("abc"))
        assertNull(CrudRules.validateAnnulReason("a".repeat(200)))
        assertNotNull(CrudRules.validateAnnulReason("a".repeat(201)))
    }

    @Test
    fun motivoSeValidaSinEspaciosDeLosExtremos() {
        assertNotNull(CrudRules.validateAnnulReason("  ab  "))
        assertNull(CrudRules.validateAnnulReason("  abc  "))
    }

    @Test
    fun notaDeAlertaEsOpcionalYTieneMaximo() {
        assertNull(CrudRules.validateAlertNote(""))
        assertNull(CrudRules.validateAlertNote("Se enfrió el estanque"))
        assertNull(CrudRules.validateAlertNote("a".repeat(300)))
        assertNotNull(CrudRules.validateAlertNote("a".repeat(301)))
    }

    @Test
    fun nombreDeDispositivoObligatorioConLetrasONumeros() {
        assertNotNull(CrudRules.validateDeviceName(""))
        assertNotNull(CrudRules.validateDeviceName("###"))
        assertNull(CrudRules.validateDeviceName("Caja de sensores 2"))
        assertNull(CrudRules.validateDeviceName("a".repeat(60)))
        assertNotNull(CrudRules.validateDeviceName("a".repeat(61)))
    }

    @Test
    fun ubicacionYNotaDeCalibracionTienenMaximo() {
        assertNull(CrudRules.validateDeviceLocation(""))
        assertNull(CrudRules.validateDeviceLocation("Sala de ordeña"))
        assertNotNull(CrudRules.validateDeviceLocation("a".repeat(61)))
        assertNull(CrudRules.validateCalibrationNote(""))
        assertNull(CrudRules.validateCalibrationNote("a".repeat(200)))
        assertNotNull(CrudRules.validateCalibrationNote("a".repeat(201)))
    }

    @Test
    fun idDeDispositivoSaleDelNombre() {
        assertEquals("caja-de-sensores-2", CrudRules.deviceIdFor("Caja de Sensores 2"))
        assertEquals("caja-nuble", CrudRules.deviceIdFor("  Caja Ñuble!! "))
        assertEquals("dispositivo", CrudRules.deviceIdFor("###"))
    }

    @Test
    fun parametrosDeCalibracionCoincidenConLasReglasDeFirestore() {
        // Los mismos valores que acepta calibraciones.parametro en firestore.rules
        val ids = CalibrationParameter.entries.map { it.id }.toSet()
        assertEquals(setOf("temperatura", "ph", "conductividad", "turbidez", "todos"), ids)
    }

    @Test
    fun parametroDesconocidoSeTrataComoTodos() {
        assertEquals(CalibrationParameter.PH, CalibrationParameter.fromId("ph"))
        assertEquals(CalibrationParameter.ALL, CalibrationParameter.fromId("otro"))
        assertEquals(CalibrationParameter.ALL, CalibrationParameter.fromId(null))
    }

    @Test
    fun permisosNuevosPorRol() {
        assertFalse(Role.LECHERO.canManageTanks)
        assertFalse(Role.LECHERO.canAnnulMeasurements)
        assertTrue(Role.ENCARGADO.canManageTanks)
        assertTrue(Role.ENCARGADO.canAnnulMeasurements)
        assertTrue(Role.ADMIN.canManageTanks)
        assertTrue(Role.ADMIN.canAnnulMeasurements)
        // Dispositivos y calibraciones: solo el administrador
        assertFalse(Role.ENCARGADO.canCalibrateDevice)
        assertTrue(Role.ADMIN.canCalibrateDevice)
    }
}
