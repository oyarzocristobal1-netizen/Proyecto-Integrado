package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun aceptaContrasenaConLargoYVariedad() {
        assertNull(PasswordRules.validateNew("Lechero2026x"))
        assertNull(PasswordRules.validateNew("Abcdefgh1j"))
    }

    @Test
    fun rechazaMuyCorta() {
        assertTrue(PasswordRules.validateNew("Abc12345")!!.contains("10 caracteres"))
        assertTrue(PasswordRules.validateNew("")!!.contains("10 caracteres"))
    }

    @Test
    fun rechazaSinMinusculaMayusculaONumero() {
        assertTrue(PasswordRules.validateNew("ABCDEFGHI1J")!!.contains("minúscula"))
        assertTrue(PasswordRules.validateNew("abcdefghi1j")!!.contains("mayúscula"))
        assertTrue(PasswordRules.validateNew("Abcdefghijk")!!.contains("número"))
    }

    @Test
    fun elLargoSeEvaluaPrimeroYElTextoDeAyudaDiceLoMismo() {
        assertEquals(10, PasswordRules.MIN_LENGTH)
        assertTrue(PasswordRules.HINT.contains("10"))
        assertTrue(PasswordRules.HINT.contains("mayúscula") && PasswordRules.HINT.contains("minúscula") && PasswordRules.HINT.contains("número"))
    }
}
