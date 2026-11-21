package com.example.myapplication.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Traduce cualquier error de Firebase a un mensaje claro en español, sin detalles internos
 * (buena practica OWASP: no exponer informacion del sistema). Un solo lugar para toda la app.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is AuthException -> error.message
    is FirebaseNetworkException -> "Sin conexión a internet. Revisa la red e inténtalo de nuevo."
    is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
    is FirebaseAuthWeakPasswordException -> "La contraseña no cumple la política: mínimo 10 caracteres, con mayúscula, minúscula y número."
    is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
    is FirebaseAuthInvalidUserException -> "Correo o contraseña incorrectos."
    // Otros errores de Authentication: con la politica de contrasenas en modo "Exigir", Firebase responde con este codigo.
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS", "ERROR_WEAK_PASSWORD" ->
            "La contraseña no cumple la política: mínimo 10 caracteres, con mayúscula, minúscula y número."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo."
        "ERROR_INVALID_EMAIL" -> "Ingresa un correo válido."
        else -> "No se pudo completar la operación. Inténtalo de nuevo."
    }
    is FirebaseFirestoreException -> firestoreMessage(code)
    else -> "No se pudo completar la operación. Inténtalo de nuevo."
}

private fun firestoreMessage(code: FirebaseFirestoreException.Code): String = when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Permiso denegado: tu rol no permite esta acción o los datos no cumplen las reglas del sistema."
    FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Tu sesión expiró. Vuelve a ingresar."
    FirebaseFirestoreException.Code.UNAVAILABLE ->
        "El servidor no está disponible (sin conexión). Los cambios se enviarán al volver la red."
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "El servidor tardó demasiado en responder. Inténtalo de nuevo."
    FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
        "Falta un índice en Firestore para esta consulta (ver docs/configurar-firebase.md)."
    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
        "Se alcanzó la cuota diaria del plan gratuito de Firebase. Inténtalo mañana."
    FirebaseFirestoreException.Code.NOT_FOUND -> "El registro ya no existe."
    FirebaseFirestoreException.Code.ALREADY_EXISTS -> "El registro ya existe."
    else -> "No se pudo completar la operación (código ${code.name}). Inténtalo de nuevo."
}
