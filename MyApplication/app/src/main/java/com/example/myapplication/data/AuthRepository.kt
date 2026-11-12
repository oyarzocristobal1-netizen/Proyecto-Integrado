package com.example.myapplication.data

import com.example.myapplication.model.Role
import com.example.myapplication.model.UserSession
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Politica de autenticacion del informe (seccion 6.1). */
const val MIN_PASSWORD_LENGTH = 10
const val REQUIRE_EMAIL_VERIFIED = true

/** Errores propios del inicio de sesion, con el mensaje que ve el usuario. */
enum class AuthError(val message: String) {
    NO_SESSION("No hay una sesión iniciada."),
    EMAIL_NOT_VERIFIED("Tu correo aún no está verificado. Te enviamos un enlace: ábrelo y vuelve a ingresar."),
    NO_PROFILE("Tu cuenta no tiene perfil en el sistema. Pide al administrador que la registre."),
    NO_ROLE("Tu cuenta no tiene un rol válido asignado. Pide al administrador que lo revise."),
    INACTIVE("Tu cuenta está desactivada. Contacta al administrador.")
}

class AuthException(val error: AuthError) : Exception(error.message)

/**
 * Inicio de sesion con Firebase Authentication (correo y contrasena) y lectura del rol
 * desde Firestore (coleccion "usuarios", campos: nombre, correo, rol, activo).
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val hasSession: Boolean get() = auth.currentUser != null

    suspend fun signIn(email: String, password: String): UserSession {
        auth.signInWithEmailAndPassword(email, password).await()
        return loadSession()
    }

    /** Valida la sesion actual: correo verificado, perfil existente, rol valido y cuenta activa. */
    suspend fun loadSession(): UserSession {
        val user = auth.currentUser ?: throw AuthException(AuthError.NO_SESSION)
        user.reload().await()
        val fresh = auth.currentUser ?: throw AuthException(AuthError.NO_SESSION)

        if (REQUIRE_EMAIL_VERIFIED && !fresh.isEmailVerified) {
            runCatching { fresh.sendEmailVerification().await() }
            auth.signOut()
            throw AuthException(AuthError.EMAIL_NOT_VERIFIED)
        }

        // Renueva el token para que las reglas de Firestore vean email_verified = true.
        fresh.getIdToken(true).await()

        val doc = try {
            db.collection("usuarios").document(fresh.uid).get().await()
        } catch (e: Exception) {
            auth.signOut()
            throw e
        }
        if (!doc.exists()) {
            auth.signOut()
            throw AuthException(AuthError.NO_PROFILE)
        }
        val role = Role.fromId(doc.getString("rol"))
        if (role == null) {
            auth.signOut()
            throw AuthException(AuthError.NO_ROLE)
        }
        if (doc.getBoolean("activo") == false) {
            auth.signOut()
            throw AuthException(AuthError.INACTIVE)
        }

        val email = fresh.email.orEmpty()
        return UserSession(
            uid = fresh.uid,
            email = email,
            name = doc.getString("nombre")?.takeIf { it.isNotBlank() } ?: email,
            role = role
        )
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut() {
        auth.signOut()
    }
}

/** Espera el resultado de una Task de Firebase sin bloquear el hilo principal. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: Exception("Error desconocido de Firebase"))
        }
    }
}
