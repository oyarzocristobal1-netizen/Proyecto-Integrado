package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.bluetooth.BluetoothDeviceModel
import com.example.myapplication.bluetooth.BluetoothService
import com.example.myapplication.model.MilkQualityReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class DemoScenario(val title: String, val description: String, val baseTemp: Float, val basePh: Float, val baseCond: Float, val baseTurb: Float) {
    NORMAL("Muestra Normal", "Leche fresca en rango óptimo (pH 6.7)", 22.0f, 6.70f, 4.8f, 85.0f),
    ACIDIC_BACTERIAL("Muestra Ácida", "Carga bacteriana / Leche lista para cortar (pH 6.2)", 24.5f, 6.25f, 5.8f, 75.0f),
    ALKALINE_MASTITIS("Muestra Mastítica", "Posible infección / pH elevado (pH 7.1)", 25.0f, 7.15f, 6.8f, 60.0f),
    DILUTED_WATER("Muestra Diluida", "Posible aguado / Turbidez reducida (pH 6.6)", 21.0f, 6.60f, 2.8f, 35.0f)
}

data class MilkQualityUiState(
    val currentReading: MilkQualityReading = MilkQualityReading(temperature = 22.0f, ph = 6.70f, conductivity = 4.8f, turbidity = 85.0f),
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val isDemoMode: Boolean = true,
    val selectedScenario: DemoScenario = DemoScenario.NORMAL,
    val history: List<MilkQualityReading> = emptyList(),
    val pairedDevices: List<BluetoothDeviceModel> = emptyList(),
    val showDeviceDialog: Boolean = false,
    val showCodeGuideDialog: Boolean = false
)

class MilkQualityViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothService = BluetoothService(application)

    private val _uiState = MutableStateFlow(MilkQualityUiState())
    val uiState: StateFlow<MilkQualityUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null

    init {
        // Collect BT Connection state
        viewModelScope.launch {
            bluetoothService.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }

        // Collect BT Readings
        viewModelScope.launch {
            bluetoothService.latestReading.collect { reading ->
                reading?.let { newReading ->
                    addReading(newReading)
                }
            }
        }

        // Start Demo Simulation by default so user can test UI immediately
        startDemoSimulation()
    }

    fun refreshDevices() {
        val devices = bluetoothService.getPairedDevices()
        _uiState.update { it.copy(pairedDevices = devices) }
    }

    fun connectDevice(address: String) {
        viewModelScope.launch {
            stopDemoSimulation()
            _uiState.update { it.copy(isDemoMode = false, showDeviceDialog = false) }
            bluetoothService.connect(address)
        }
    }

    fun disconnectBluetooth() {
        viewModelScope.launch {
            bluetoothService.disconnect()
        }
    }

    fun toggleDemoMode(enable: Boolean) {
        _uiState.update { it.copy(isDemoMode = enable) }
        if (enable) {
            disconnectBluetooth()
            startDemoSimulation()
        } else {
            stopDemoSimulation()
        }
    }

    fun setDemoScenario(scenario: DemoScenario) {
        _uiState.update { it.copy(selectedScenario = scenario) }
        if (_uiState.value.isDemoMode) {
            startDemoSimulation()
        }
    }

    fun setShowDeviceDialog(show: Boolean) {
        if (show) refreshDevices()
        _uiState.update { it.copy(showDeviceDialog = show) }
    }

    fun setShowCodeGuideDialog(show: Boolean) {
        _uiState.update { it.copy(showCodeGuideDialog = show) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    private fun addReading(reading: MilkQualityReading) {
        _uiState.update { currentState ->
            val updatedHistory = (listOf(reading) + currentState.history).take(50)
            currentState.copy(
                currentReading = reading,
                history = updatedHistory
            )
        }
    }

    private fun startDemoSimulation() {
        stopDemoSimulation()
        simulationJob = viewModelScope.launch {
            while (true) {
                val scenario = _uiState.value.selectedScenario
                // Add small random fluctuation to simulate real sensor noise
                val tempNoise = (Random.nextFloat() - 0.5f) * 0.4f
                val phNoise = (Random.nextFloat() - 0.5f) * 0.04f
                val condNoise = (Random.nextFloat() - 0.5f) * 0.1f
                val turbNoise = (Random.nextFloat() - 0.5f) * 1.0f

                val reading = MilkQualityReading(
                    temperature = (scenario.baseTemp + tempNoise).coerceIn(0.0f, 50.0f),
                    ph = ((scenario.basePh + phNoise) * 100).let { kotlin.math.round(it) / 100f },
                    conductivity = ((scenario.baseCond + condNoise) * 100).let { kotlin.math.round(it) / 100f },
                    turbidity = ((scenario.baseTurb + turbNoise) * 10).let { kotlin.math.round(it) / 10f }
                )

                addReading(reading)
                delay(2500) // update every 2.5s matching Arduino loop
            }
        }
    }

    private fun stopDemoSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDemoSimulation()
    }
}
