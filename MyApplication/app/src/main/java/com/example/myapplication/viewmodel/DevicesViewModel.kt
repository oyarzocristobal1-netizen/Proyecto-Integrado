package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.Calibration
import com.example.myapplication.data.Device
import com.example.myapplication.data.DeviceResult
import com.example.myapplication.data.DevicesRepository
import com.example.myapplication.data.toUserMessage
import com.example.myapplication.model.CalibrationParameter
import com.example.myapplication.model.CrudRules
import com.example.myapplication.model.UserSession
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val loading: Boolean = true,
    /** Dispositivo del que se ven las calibraciones. */
    val selectedId: String? = null,
    val calibrations: List<Calibration> = emptyList(),
    val calibrationsLoading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null
) {
    val selected: Device? get() = devices.firstOrNull { it.id == selectedId }
}

/**
 * Dispositivos y calibraciones: solo el administrador (lo exigen las reglas de Firestore).
 * Los dispositivos se desactivan, no se borran; cada calibracion queda con fecha del servidor y responsable.
 * Las escrituras requieren conexion: son tareas de administracion, no de sala de ordeña.
 */
class DevicesViewModel(private val repository: DevicesRepository) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    private var devicesListener: ListenerRegistration? = null
    private var calibrationsListener: ListenerRegistration? = null

    fun start() {
        if (devicesListener != null) return
        devicesListener = repository.listenDevices(
            onChange = { list -> _state.update { it.copy(devices = list, loading = false) } },
            onError = { e ->
                _state.update { it.copy(loading = false) }
                showError(e)
            }
        )
    }

    fun selectDevice(id: String?) {
        calibrationsListener?.remove()
        calibrationsListener = null
        _state.update { it.copy(selectedId = id, calibrations = emptyList(), calibrationsLoading = id != null) }
        if (id == null) return
        calibrationsListener = repository.listenCalibrations(
            deviceId = id,
            onChange = { list -> _state.update { it.copy(calibrations = list, calibrationsLoading = false) } },
            onError = { e ->
                _state.update { it.copy(calibrationsLoading = false) }
                showError(e)
            }
        )
    }

    fun createDevice(name: String, location: String, onDone: () -> Unit) {
        validateDevice(name, location)?.let {
            showMessage(it)
            return
        }
        val id = CrudRules.deviceIdFor(name)
        execute(failure = "No se pudo crear el dispositivo") {
            when (repository.createDevice(id, name, location)) {
                DeviceResult.CREATED -> {
                    showMessage("Dispositivo creado.")
                    onDone()
                }
                DeviceResult.ALREADY_EXISTS ->
                    showMessage("Ya existe un dispositivo con ese nombre (ID \"$id\"). Usa otro nombre.")
            }
        }
    }

    fun updateDevice(device: Device, name: String, location: String, active: Boolean, onDone: () -> Unit) {
        validateDevice(name, location)?.let {
            showMessage(it)
            return
        }
        execute(failure = "No se pudo guardar el dispositivo") {
            repository.updateDevice(device, name, location, active)
            showMessage("Dispositivo actualizado.")
            onDone()
        }
    }

    fun addCalibration(
        device: Device,
        parameter: CalibrationParameter,
        note: String,
        session: UserSession,
        onDone: () -> Unit
    ) {
        if (!session.role.canCalibrateDevice) {
            showMessage("Solo el administrador puede registrar calibraciones.")
            return
        }
        if (!device.active) {
            showMessage("El dispositivo está desactivado: actívalo para registrar una calibración.")
            return
        }
        CrudRules.validateCalibrationNote(note)?.let {
            showMessage(it)
            return
        }
        execute(failure = "No se pudo registrar la calibración") {
            repository.addCalibration(device, parameter, note, session)
            showMessage("Calibración registrada.")
            onDone()
        }
    }

    fun updateCalibrationNote(calibration: Calibration, note: String, onDone: () -> Unit) {
        CrudRules.validateCalibrationNote(note)?.let {
            showMessage(it)
            return
        }
        execute(failure = "No se pudo guardar la nota") {
            repository.updateCalibrationNote(calibration.id, note)
            showMessage("Nota actualizada.")
            onDone()
        }
    }

    private fun validateDevice(name: String, location: String): String? =
        CrudRules.validateDeviceName(name) ?: CrudRules.validateDeviceLocation(location)

    private fun execute(failure: String, block: suspend () -> Unit) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                withTimeout(WRITE_TIMEOUT_MS) { block() }
            } catch (e: TimeoutCancellationException) {
                // Sin internet, Firestore deja la escritura en cola y no confirma: no se deja la pantalla esperando.
                showMessage("$failure: el servidor no respondió (¿sin conexión?). Si el cambio quedó en cola, se aplicará al volver la red.")
            } catch (e: Exception) {
                showMessage("$failure: " + friendly(e))
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }

    private fun showError(e: Exception) = showMessage(friendly(e))

    private fun friendly(e: Exception): String =
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Permiso denegado: solo un administrador activo puede gestionar dispositivos."
        } else {
            e.toUserMessage()
        }

    override fun onCleared() {
        devicesListener?.remove()
        calibrationsListener?.remove()
        super.onCleared()
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 15_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { DevicesViewModel(appContainer().devicesRepository) }
        }
    }
}
