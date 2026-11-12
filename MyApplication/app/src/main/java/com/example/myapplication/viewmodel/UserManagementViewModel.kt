package com.example.myapplication.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.MIN_PASSWORD_LENGTH
import com.example.myapplication.data.ManagedUser
import com.example.myapplication.data.UserAdminRepository
import com.example.myapplication.model.Role
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserManagementUiState(
    val users: List<ManagedUser> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** Mensaje de exito o error para mostrar una vez (snackbar). */
    val message: String? = null,
    /** Error de validacion del formulario abierto. */
    val formError: String? = null
)

class UserManagementViewModel : ViewModel() {

    private val repository = UserAdminRepository()

    private val _state = MutableStateFlow(UserManagementUiState())
    val state: StateFlow<UserManagementUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val users = repository.listUsers()
                _state.update { it.copy(users = users, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = friendlyMessage(e)) }
            }
        }
    }

    fun createUser(name: String, email: String, password: String, role: Role, onDone: () -> Unit) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val error = when {
            cleanName.isEmpty() -> "Escribe el nombre del trabajador."
            !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> "Ingresa un correo válido."
            password.length < MIN_PASSWORD_LENGTH -> "La contraseña inicial debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(formError = error) }
            return
        }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                repository.createUser(cleanName, cleanEmail, password, role)
                _state.update {
                    it.copy(
                        saving = false,
                        message = "Cuenta creada para $cleanName (${role.label}). Le llegará un correo para verificarla."
                    )
                }
                onDone()
                load()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, formError = friendlyMessage(e)) }
            }
        }
    }

    fun updateUser(user: ManagedUser, name: String, role: Role, active: Boolean, onDone: () -> Unit) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _state.update { it.copy(formError = "El nombre no puede quedar vacío.") }
            return
        }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                repository.updateUser(user.uid, cleanName, role, active)
                _state.update { it.copy(saving = false, message = "Cambios guardados para $cleanName.") }
                onDone()
                load()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, formError = friendlyMessage(e)) }
            }
        }
    }

    fun sendPasswordReset(user: ManagedUser) {
        viewModelScope.launch {
            val message = try {
                repository.sendPasswordReset(user.email)
                "Enviamos a ${user.email} un enlace para restablecer la contraseña."
            } catch (e: Exception) {
                friendlyMessage(e)
            }
            _state.update { it.copy(message = message) }
        }
    }

    fun clearFormError() = _state.update { it.copy(formError = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
        is FirebaseAuthWeakPasswordException -> "La contraseña es demasiado débil. Usa una más larga o variada."
        is FirebaseAuthInvalidCredentialsException -> "El correo no tiene un formato válido."
        is FirebaseNetworkException -> "Sin conexión a internet. Inténtalo de nuevo."
        is FirebaseFirestoreException ->
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Permiso denegado: solo un administrador activo puede gestionar usuarios."
            } else {
                "No se pudo acceder a los usuarios. Inténtalo de nuevo."
            }
        else -> "No se pudo completar la operación. Inténtalo de nuevo."
    }
}
