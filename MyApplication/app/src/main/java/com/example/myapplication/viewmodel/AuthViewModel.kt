package com.example.myapplication.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.MIN_PASSWORD_LENGTH
import com.example.myapplication.data.toUserMessage
import com.example.myapplication.model.UserSession
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    /** Revisando si ya hay una sesion guardada al abrir la app. */
    data object Checking : AuthUiState

    data class LoggedOut(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null
    ) : AuthUiState

    data class LoggedIn(val session: UserSession) : AuthUiState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        if (!repository.hasSession) {
            _state.value = AuthUiState.LoggedOut()
            return
        }
        viewModelScope.launch {
            _state.value = try {
                AuthUiState.LoggedIn(repository.loadSession())
            } catch (e: Exception) {
                repository.signOut()
                AuthUiState.LoggedOut(error = friendlyMessage(e))
            }
        }
    }

    fun login(email: String, password: String) {
        val cleanEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _state.value = AuthUiState.LoggedOut(error = "Ingresa un correo válido.")
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            _state.value = AuthUiState.LoggedOut(
                error = "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            )
            return
        }
        _state.value = AuthUiState.LoggedOut(loading = true)
        viewModelScope.launch {
            _state.value = try {
                AuthUiState.LoggedIn(repository.signIn(cleanEmail, password))
            } catch (e: Exception) {
                AuthUiState.LoggedOut(error = friendlyMessage(e))
            }
        }
    }

    fun resetPassword(email: String) {
        val cleanEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _state.value = AuthUiState.LoggedOut(
                error = "Escribe tu correo arriba para enviarte el enlace de recuperación."
            )
            return
        }
        _state.value = AuthUiState.LoggedOut(loading = true)
        viewModelScope.launch {
            _state.value = try {
                repository.sendPasswordReset(cleanEmail)
                AuthUiState.LoggedOut(
                    info = "Si el correo está registrado, te enviamos un enlace para restablecer la contraseña."
                )
            } catch (e: Exception) {
                AuthUiState.LoggedOut(error = friendlyMessage(e))
            }
        }
    }

    fun logout() {
        repository.signOut()
        _state.value = AuthUiState.LoggedOut()
    }

    /** Mensajes claros y sin detalles internos (buena practica OWASP: no exponer informacion del sistema). */
    private fun friendlyMessage(e: Exception): String =
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "No se pudo leer tu perfil (permiso denegado). Pide al administrador que revise tu cuenta."
        } else {
            e.toUserMessage()
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(appContainer().authRepository) }
        }
    }
}
