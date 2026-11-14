package com.example.myapplication.data

import com.example.myapplication.model.Role
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Usuario tal como lo ve el administrador en la gestion de usuarios. */
data class ManagedUser(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role?,
    val active: Boolean
)

/**
 * Gestion de usuarios (CU5, solo administrador): listar, crear, cambiar rol o estado y
 * enviar correo para restablecer la contrasena. Las reglas de Firestore solo permiten
 * escribir en "usuarios" al administrador.
 */
class UserAdminRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val users = db.collection(USERS)

    suspend fun listUsers(): List<ManagedUser> =
        users.get().await().documents
            .map { doc ->
                ManagedUser(
                    uid = doc.id,
                    name = doc.getString("nombre").orEmpty(),
                    email = doc.getString("correo").orEmpty(),
                    role = Role.fromId(doc.getString("rol")),
                    active = doc.getBoolean("activo") == true
                )
            }
            .sortedWith(compareBy({ it.role?.ordinal ?: Int.MAX_VALUE }, { it.name.lowercase() }))

    /**
     * Crea la cuenta en Authentication y su perfil en Firestore.
     * Se usa una segunda instancia de Firebase para que la sesion del administrador no se cierre
     * (al crear una cuenta, Firebase inicia sesion con ella en la instancia que la creo).
     */
    suspend fun createUser(name: String, email: String, password: String, role: Role): ManagedUser {
        val secondaryAuth = secondaryAuth()
        val created = secondaryAuth.createUserWithEmailAndPassword(email, password).await()
        val newUser = created.user ?: throw IllegalStateException("Firebase no devolvió la cuenta creada.")
        try {
            users.document(newUser.uid).set(
                mapOf(
                    "nombre" to name,
                    "correo" to email,
                    "rol" to role.id,
                    "activo" to true
                )
            ).await()
            // El trabajador recibe el enlace de verificacion en su correo.
            runCatching { newUser.sendEmailVerification().await() }
        } catch (e: Exception) {
            // Si no se pudo guardar el perfil, se deshace la cuenta para no dejarla a medias.
            runCatching { newUser.delete().await() }
            throw e
        } finally {
            secondaryAuth.signOut()
        }
        return ManagedUser(newUser.uid, name, email, role, active = true)
    }

    suspend fun updateUser(uid: String, name: String, role: Role, active: Boolean) {
        users.document(uid).update(
            mapOf(
                "nombre" to name,
                "rol" to role.id,
                "activo" to active
            )
        ).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    private fun secondaryAuth(): FirebaseAuth {
        val defaultApp = FirebaseApp.getInstance()
        val context = defaultApp.applicationContext
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == SECONDARY_APP }
            ?: FirebaseApp.initializeApp(context, defaultApp.options, SECONDARY_APP)
        return FirebaseAuth.getInstance(app)
    }

    private companion object {
        const val USERS = "usuarios"
        const val SECONDARY_APP = "alta-usuarios"
    }
}
