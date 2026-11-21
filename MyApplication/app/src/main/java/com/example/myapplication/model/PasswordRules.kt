package com.example.myapplication.model

/**
 * Politica de contrasenas para cuentas nuevas. Es la misma que se activa en Firebase Authentication
 * (Configuracion > Politica de contrasenas): el servidor la exige tambien, la app la valida antes
 * para mostrar un mensaje claro. El inicio de sesion NO la aplica: las cuentas existentes siguen entrando.
 */
object PasswordRules {
    const val MIN_LENGTH = 10
    const val HINT = "Mínimo $MIN_LENGTH caracteres, con mayúscula, minúscula y número."

    /** Mensaje si la contrasena no cumple la politica, o null si esta bien. */
    fun validateNew(password: String): String? = when {
        password.length < MIN_LENGTH -> "La contraseña debe tener al menos $MIN_LENGTH caracteres."
        password.none { it.isLowerCase() } -> "La contraseña debe incluir una letra minúscula."
        password.none { it.isUpperCase() } -> "La contraseña debe incluir una letra mayúscula."
        password.none { it.isDigit() } -> "La contraseña debe incluir un número."
        else -> null
    }
}
