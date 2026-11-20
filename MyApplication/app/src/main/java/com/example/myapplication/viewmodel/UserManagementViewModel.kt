package com.example.myapplication.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.DeletionRepository
import com.example.myapplication.data.MIN_PASSWORD_LENGTH
import com.example.myapplication.data.ManagedUser
import com.example.myapplication.data.UserAdminRepository
import com.example.myapplication.data.toUserMessage
import com.example.myapplication.model.Role
import com.example.myapplication.model.UserSession
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
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

class UserManagementViewModel(
    private val repository: UserAdminRepository,
    private val deletions: DeletionRepository
) : ViewModel() {

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
            cleanName.length > MAX_NAME -> "El nombre no puede superar $MAX_NAME caracteres."
            cleanEmail.length > MAX_EMAIL -> "El correo no puede superar $MAX_EMAIL caracteres."
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
        val error = when {
            cleanName.isEmpty() -> "El nombre no puede quedar vacío."
            cleanName.length > MAX_NAME -> "El nombre no puede superar $MAX_NAME caracteres."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(formError = error) }
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

    /**
     * Elimina el perfil de una cuenta (solo administrador, nunca la propia). Pierde el acceso a la app;
     * sus mediciones se conservan con su nombre. Lo normal es desactivarla.
     */
    fun deleteUser(user: ManagedUser, session: UserSession, onDone: () -> Unit) {
        if (!session.role.canDeleteRecords) {
            _state.update { it.copy(message = "Solo el administrador puede eliminar cuentas.") }
            return
        }
        if (user.uid == session.uid) {
            _state.update { it.copy(message = "No puedes eliminar tu propia cuenta.") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                deletions.deleteUserProfile(user, session)
                _state.update { it.copy(saving = false, message = "Cuenta de ${user.name.ifBlank { user.email }} eliminada.") }
                onDone()
                load()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = friendlyMessage(e)) }
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

    private fun friendlyMessage(e: Exception): String = when {
        // Al crear una cuenta, este error de Firebase significa correo mal escrito.
        e is FirebaseAuthInvalidCredentialsException -> "El correo no tiene un formato válido."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permiso denegado: solo un administrador activo puede gestionar usuarios."
        else -> e.toUserMessage()
    }

    companion object {
        /** Los mismos limites que validan las reglas de Firestore. */
        const val MAX_NAME = 80
        const val MAX_EMAIL = 120

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { UserManagementViewModel(appContainer().userAdminRepository, appContainer().deletionRepository) }
        }
    }
}
