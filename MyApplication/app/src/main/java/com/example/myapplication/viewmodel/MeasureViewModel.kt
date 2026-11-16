package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.bluetooth.BluetoothDeviceModel
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.StabilityState
import com.example.myapplication.model.StabilityTracker
import com.example.myapplication.sensor.DemoReadingGenerator
import com.example.myapplication.sensor.DemoScenario
import com.example.myapplication.sensor.SensorSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** De donde salen las lecturas que se ven en la pantalla Medir. */
enum class MeasureMode(val label: String) {
    /** Datos simulados para probar la app sin la caja (se guardan marcados como SIMULADOS). */
    DEMO("Simulación"),

    /** Caja de sensores por Bluetooth (pendiente de validar con el hardware real). */
    DEVICE("Caja de sensores")
}

data class MeasureUiState(
    val mode: MeasureMode = MeasureMode.DEMO,
    val scenario: DemoScenario = DemoScenario.NORMAL,
    /** Ultima lectura. null = todavia no llega ninguna; la app nunca muestra valores inventados. */
    val reading: MilkQualityReading? = null,
    val stability: StabilityState = StabilityState(isStable = false, samples = 0, required = STABILITY_WINDOW),
    val connection: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val pairedDevices: List<BluetoothDeviceModel> = emptyList(),
    val showDeviceDialog: Boolean = false,
    /** Tramas de la caja descartadas por incompletas, corruptas o fuera de limites. */
    val rejectedFrames: Int = 0,
    val lastRejectReason: String? = null,
    /** Hora actual (se actualiza cada segundo) para calcular la antiguedad de la lectura. */
    val now: Long = System.currentTimeMillis()
) {
    val readingAgeSeconds: Long?
        get() = reading?.let { ((now - it.timestamp) / 1000).coerceAtLeast(0) }

    /** La lectura sigue representando la muestra actual (no tiene mas de 15 s). */
    val isReadingFresh: Boolean
        get() = reading != null && now - reading.timestamp <= MeasurementValidator.MAX_READING_AGE_MS

    val isConnected: Boolean get() = connection is BluetoothConnectionState.Connected

    /** Se puede registrar: hay lectura reciente y estable. El lote y el resto se validan al registrar. */
    val canRecord: Boolean get() = reading != null && isReadingFresh && stability.isStable

    /** Motivo por el que aun no se puede registrar (para mostrarlo bajo el boton). */
    val recordHint: String?
        get() = when {
            reading == null && mode == MeasureMode.DEVICE && !isConnected -> "Conecta la caja de sensores para recibir lecturas."
            reading == null -> "Esperando la primera lectura…"
            !isReadingFresh -> "La última lectura tiene más de 15 s. Espera una nueva."
            !stability.isStable -> "Estabilizando la lectura (${stability.samples}/${stability.required}). Mantén el sensor en la muestra."
            else -> null
        }
}

private const val STABILITY_WINDOW = 4
private const val DEMO_INTERVAL_MS = 2_500L
private const val CLOCK_INTERVAL_MS = 1_000L

/**
 * Lecturas en vivo de la pantalla Medir: simuladas (modo DEMO) o de la caja por Bluetooth.
 * Solo muestra lecturas; registrar la medicion en Firestore lo hace RecordsViewModel.
 */
class MeasureViewModel(
    private val sensor: SensorSource,
    private val demo: DemoReadingGenerator = DemoReadingGenerator()
) : ViewModel() {

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    private val stability = StabilityTracker(window = STABILITY_WINDOW)
    private var demoJob: Job? = null
    private var clockJob: Job? = null
    private var screenVisible = false

    init {
        viewModelScope.launch {
            sensor.connectionState.collect { c -> _state.update { it.copy(connection = c) } }
        }
        viewModelScope.launch {
            sensor.latestReading.collect { reading ->
                // Las lecturas de la caja solo cuentan en modo "Caja de sensores".
                if (reading != null && _state.value.mode == MeasureMode.DEVICE) accept(reading)
            }
        }
        viewModelScope.launch {
            sensor.rejectedFrames.collect { n -> _state.update { it.copy(rejectedFrames = n) } }
        }
        viewModelScope.launch {
            sensor.lastRejectReason.collect { r -> _state.update { it.copy(lastRejectReason = r) } }
        }
    }

    /** La pantalla Medir avisa cuando se muestra u oculta: la simulacion solo corre mientras se ve. */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) {
            startClock()
            if (_state.value.mode == MeasureMode.DEMO) startDemo()
        } else {
            stopDemo()
            clockJob?.cancel()
            clockJob = null
        }
    }

    fun setMode(mode: MeasureMode) {
        if (_state.value.mode == mode) return
        stability.reset()
        _state.update {
            it.copy(mode = mode, reading = null, stability = stability.state(), showDeviceDialog = false)
        }
        when (mode) {
            MeasureMode.DEMO -> {
                if (_state.value.isConnected) viewModelScope.launch { sensor.disconnect() }
                if (screenVisible) startDemo()
            }
            MeasureMode.DEVICE -> stopDemo()
        }
    }

    fun setScenario(scenario: DemoScenario) {
        if (_state.value.scenario == scenario) return
        // Otra muestra: la estabilidad se vuelve a medir desde cero.
        stability.reset()
        _state.update { it.copy(scenario = scenario, reading = null, stability = stability.state()) }
        if (_state.value.mode == MeasureMode.DEMO && screenVisible) startDemo()
    }

    fun openDeviceDialog() {
        refreshDevices()
        _state.update { it.copy(showDeviceDialog = true) }
    }

    fun closeDeviceDialog() = _state.update { it.copy(showDeviceDialog = false) }

    fun refreshDevices() {
        _state.update { it.copy(pairedDevices = sensor.pairedDevices()) }
    }

    fun hasBluetoothPermission(): Boolean = sensor.hasPermission()

    fun connect(address: String) {
        stability.reset()
        _state.update {
            it.copy(
                showDeviceDialog = false,
                mode = MeasureMode.DEVICE,
                reading = null,
                stability = stability.state()
            )
        }
        stopDemo()
        viewModelScope.launch { sensor.connect(address) }
    }

    fun disconnect() {
        viewModelScope.launch { sensor.disconnect() }
    }

    private fun accept(reading: MilkQualityReading) {
        val st = stability.add(reading)
        _state.update { it.copy(reading = reading, stability = st, now = System.currentTimeMillis()) }
    }

    private fun startDemo() {
        stopDemo()
        demoJob = viewModelScope.launch {
            while (isActive) {
                accept(demo.next(_state.value.scenario, System.currentTimeMillis()))
                delay(DEMO_INTERVAL_MS)
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
    }

    private fun startClock() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(now = System.currentTimeMillis()) }
                delay(CLOCK_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        stopDemo()
        // La conexion vive fuera del ViewModel: se cierra al salir de la app.
        sensor.close()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MeasureViewModel(appContainer().sensorSource) }
        }
    }
}
