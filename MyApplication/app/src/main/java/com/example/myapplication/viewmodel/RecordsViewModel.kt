package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.ConnectivityObserver
import com.example.myapplication.data.HISTORY_PAGE_SIZE
import com.example.myapplication.data.Lot
import com.example.myapplication.data.MilkAlert
import com.example.myapplication.data.RecordsRepository
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.data.toUserMessage
import com.example.myapplication.model.DashboardStats
import com.example.myapplication.model.DashboardSummary
import com.example.myapplication.model.MeasurementPoint
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.QualityRangesHolder
import com.example.myapplication.model.RecordRules
import com.example.myapplication.model.Role
import com.example.myapplication.model.StabilityState
import com.example.myapplication.model.UserSession
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/** Filtro por origen: mediciones del sensor, simuladas (modo DEMO) o todas. */
enum class OriginFilter(val label: String) {
    ALL("Todas"),
    SENSOR("Sensor"),
    SIMULATED("Simuladas");

    fun matches(simulated: Boolean): Boolean = when (this) {
        ALL -> true
        SENSOR -> !simulated
        SIMULATED -> simulated
    }
}

/** Filtro por resultado del semaforo. */
enum class StatusFilter(val label: String, val status: String?) {
    ALL("Todos", null),
    GREEN("Conforme", "verde"),
    AMBER("Precaución", "ambar"),
    RED("Fuera de rango", "rojo")
}

data class RecordsUiState(
    /** El telefono tiene internet (si no, lo registrado se guarda y se envia despues). */
    val online: Boolean = true,
    val lots: List<Lot> = emptyList(),
    /** Lote donde se registran las mediciones. */
    val currentLotId: String? = null,

    // Historial
    val historyLotFilter: String? = null,
    val historyOrigin: OriginFilter = OriginFilter.ALL,
    val historyStatus: StatusFilter = StatusFilter.ALL,
    val history: List<SavedMeasurement> = emptyList(),
    val historyLoading: Boolean = true,
    val historyHasMore: Boolean = false,
    val historyLimit: Long = HISTORY_PAGE_SIZE,

    // Panel principal (ultimos 7 dias)
    val recent: List<SavedMeasurement> = emptyList(),
    val recentLoading: Boolean = true,
    val dashboardOrigin: OriginFilter = OriginFilter.ALL,
    val dashboard: DashboardSummary? = null,

    // Alertas
    /** Alertas sin atender (para el contador de la pestaña). */
    val pendingAlerts: List<MilkAlert> = emptyList(),
    /** Lista de la pantalla de alertas (pendientes o todas, segun el filtro). */
    val alerts: List<MilkAlert> = emptyList(),
    val alertsOnlyPending: Boolean = true,
    val alertsLoading: Boolean = true,

    /** Rangos de calidad vigentes (configuracion/rangos). */
    val ranges: QualityRanges = QualityRangesHolder.current,
    /** Dispositivos que no estan registrados en Firestore (sin ellos, las reglas rechazan la medicion). */
    val missingDevices: Set<String> = emptySet(),
    val saving: Boolean = false,
    /** Hora de la ultima lectura registrada, para no registrar dos veces la misma. */
    val lastRecordedTimestamp: Long? = null,
    /** Mensaje para mostrar una vez (snackbar). */
    val message: String? = null
) {
    val currentLot: Lot? get() = lots.firstOrNull { it.id == currentLotId }
    val todayLots: List<Lot> get() = lots.filter { isToday(it.date) }
    fun lotName(lotId: String): String = lots.firstOrNull { it.id == lotId }?.tank ?: lotId

    /** Historial con los filtros de origen y semaforo aplicados a las mediciones cargadas. */
    val filteredHistory: List<SavedMeasurement>
        get() = history.filter { m ->
            historyOrigin.matches(m.isSimulated) && (historyStatus.status == null || m.status == historyStatus.status)
        }

    /** Mediciones guardadas en el telefono que el servidor aun no confirma. */
    val pendingSyncCount: Int get() = recent.count { it.pendingSync }

    val recentSimulatedCount: Int get() = recent.count { it.isSimulated }
}

private fun isToday(date: Date): Boolean {
    val a = Calendar.getInstance().apply { time = date }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private const val DASHBOARD_DAYS = 7
private const val MAX_NOTE = 200

/**
 * Lotes, registro de mediciones, historial, panel, alertas y rangos (Firestore), segun el rol.
 * Lo comparten todas las pantallas de la sesion.
 */
class RecordsViewModel(
    private val repository: RecordsRepository,
    connectivity: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow(RecordsUiState())
    val state: StateFlow<RecordsUiState> = _state.asStateFlow()

    private var session: UserSession? = null
    private var lotsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null
    private var recentListener: ListenerRegistration? = null
    private var pendingAlertsListener: ListenerRegistration? = null
    private var alertsListener: ListenerRegistration? = null
    private var rangesListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(online = online) } }
        }
    }

    /** Se llama al iniciar sesion. */
    fun start(newSession: UserSession) {
        if (session?.uid == newSession.uid && session?.role == newSession.role) return
        stop()
        session = newSession
        _state.update { RecordsUiState(online = it.online) }
        lotsListener = repository.listenRecentLots(
            onChange = { lots ->
                _state.update { st ->
                    // Si no hay lote elegido, se toma el mas reciente de hoy.
                    val current = st.currentLotId?.takeIf { id -> lots.any { it.id == id } }
                        ?: lots.firstOrNull { isToday(it.date) }?.id
                    st.copy(lots = lots, currentLotId = current)
                }
            },
            onError = ::showError
        )
        listenHistory()
        listenRecent()
        pendingAlertsListener = repository.listenAlerts(
            onlyPending = true,
            onChange = { list -> _state.update { it.copy(pendingAlerts = list) } },
            onError = ::showError
        )
        listenAlerts()
        rangesListener = repository.listenRanges(
            onChange = { ranges ->
                QualityRangesHolder.current = ranges
                _state.update { it.copy(ranges = ranges) }
            },
            onError = ::showError
        )
        ensureDevices(newSession)
    }

    /** Se llama al cerrar sesion: deja de escuchar Firestore antes de salir. */
    fun stop() {
        listOf(lotsListener, historyListener, recentListener, pendingAlertsListener, alertsListener, rangesListener)
            .forEach { it?.remove() }
        lotsListener = null
        historyListener = null
        recentListener = null
        pendingAlertsListener = null
        alertsListener = null
        rangesListener = null
        session = null
    }

    /**
     * La caja y el simulador deben estar registrados para guardar mediciones (lo exigen las reglas).
     * Si faltan y entra el administrador, la app los registra; si entra otro rol, se avisa.
     */
    private fun ensureDevices(current: UserSession) {
        viewModelScope.launch {
            val missing = mutableSetOf<String>()
            for (id in listOf(RecordRules.SIMULATOR_DEVICE_ID, RecordRules.DEVICE_ID)) {
                // null = no se pudo saber (por ejemplo, sin conexion): no se marca como faltante.
                if (repository.deviceExists(id) != false) continue
                if (current.role == Role.ADMIN) {
                    try {
                        repository.registerDevice(id)
                    } catch (e: Exception) {
                        missing += id
                        showMessage("No se pudo registrar el dispositivo \"$id\": " + e.toUserMessage())
                    }
                } else {
                    missing += id
                }
            }
            _state.update { it.copy(missingDevices = missing) }
            if (current.role != Role.ADMIN && RecordRules.SIMULATOR_DEVICE_ID in missing) {
                showMessage("El simulador no está registrado. Pide al administrador que ingrese a la app una vez.")
            }
        }
    }

    // ----------------------------------------------------------------- historial

    private fun listenHistory() {
        val current = session ?: return
        historyListener?.remove()
        _state.update { it.copy(historyLoading = true) }
        historyListener = repository.listenHistory(
            session = current,
            lotId = _state.value.historyLotFilter,
            limit = _state.value.historyLimit,
            onChange = { list, hasMore ->
                _state.update { it.copy(history = list, historyHasMore = hasMore, historyLoading = false) }
            },
            onError = { e ->
                _state.update { it.copy(historyLoading = false) }
                showError(e)
            }
        )
    }

    fun setHistoryLotFilter(lotId: String?) {
        if (_state.value.historyLotFilter == lotId) return
        _state.update { it.copy(historyLotFilter = lotId, historyLimit = HISTORY_PAGE_SIZE) }
        listenHistory()
    }

    fun setHistoryOrigin(filter: OriginFilter) = _state.update { it.copy(historyOrigin = filter) }

    fun setHistoryStatus(filter: StatusFilter) = _state.update { it.copy(historyStatus = filter) }

    /** Carga la siguiente pagina del historial. */
    fun loadMoreHistory() {
        if (!_state.value.historyHasMore || _state.value.historyLoading) return
        _state.update { it.copy(historyLimit = it.historyLimit + HISTORY_PAGE_SIZE) }
        listenHistory()
    }

    // --------------------------------------------------------------------- panel

    private fun listenRecent() {
        val current = session ?: return
        recentListener?.remove()
        val since = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(DASHBOARD_DAYS - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        recentListener = repository.listenSince(
            session = current,
            since = since,
            onChange = { list -> _state.update { withDashboard(it.copy(recent = list, recentLoading = false)) } },
            onError = { e ->
                _state.update { it.copy(recentLoading = false) }
                showError(e)
            }
        )
    }

    fun setDashboardOrigin(filter: OriginFilter) = _state.update { withDashboard(it.copy(dashboardOrigin = filter)) }

    /** Recalcula los indicadores del panel con el filtro de origen elegido. */
    fun refreshDashboard() = _state.update { withDashboard(it) }

    private fun withDashboard(st: RecordsUiState): RecordsUiState {
        val points = st.recent
            .filter { st.dashboardOrigin.matches(it.isSimulated) }
            .map { MeasurementPoint(it.dateTime.time, it.temperature, it.ph, it.status, it.isSimulated) }
        return st.copy(dashboard = DashboardStats.compute(points, System.currentTimeMillis(), dayCount = DASHBOARD_DAYS))
    }

    // ------------------------------------------------------------------- alertas

    private fun listenAlerts() {
        alertsListener?.remove()
        _state.update { it.copy(alertsLoading = true) }
        alertsListener = repository.listenAlerts(
            onlyPending = _state.value.alertsOnlyPending,
            onChange = { list -> _state.update { it.copy(alerts = list, alertsLoading = false) } },
            onError = { e ->
                _state.update { it.copy(alertsLoading = false) }
                showError(e)
            }
        )
    }

    fun setAlertsOnlyPending(onlyPending: Boolean) {
        if (_state.value.alertsOnlyPending == onlyPending) return
        _state.update { it.copy(alertsOnlyPending = onlyPending) }
        listenAlerts()
    }

    /** Solo encargado y administrador (lo exigen las reglas). */
    fun markAlertAttended(alert: MilkAlert) {
        val current = session ?: return
        if (!current.role.canAttendAlerts || alert.attended) return
        write(
            done = "Alerta marcada como atendida.",
            failure = "No se pudo marcar la alerta",
            onDone = {}
        ) { repository.markAlertAttended(alert.id) }
    }

    // -------------------------------------------------------------------- rangos

    /** Solo el administrador (lo exigen las reglas). */
    fun saveRanges(ranges: QualityRanges, onDone: () -> Unit) {
        val current = session ?: return
        if (!current.role.canEditQualityRanges) {
            showMessage("Tu rol no permite modificar los rangos.")
            return
        }
        val error = ranges.validate()
        if (error != null) {
            showMessage(error)
            return
        }
        write(
            done = "Rangos de calidad actualizados.",
            failure = "No se pudieron guardar los rangos",
            onDone = onDone
        ) { repository.saveRanges(ranges) }
    }

    // --------------------------------------------------------------------- lotes

    fun selectLot(lotId: String) {
        _state.update { it.copy(currentLotId = lotId) }
    }

    /** Usa el lote de hoy de ese estanque, o lo crea si no existe. */
    fun openTodayLot(tank: String, onDone: () -> Unit) {
        val current = session ?: return
        MeasurementValidator.validateTankName(tank)?.let {
            showMessage(it)
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val lot = repository.getOrCreateTodayLot(tank, current) { e ->
                    showMessage("El servidor no aceptó el lote: " + e.toUserMessage())
                }
                _state.update { st ->
                    val lots = if (st.lots.any { it.id == lot.id }) st.lots else listOf(lot) + st.lots
                    st.copy(lots = lots, currentLotId = lot.id, saving = false)
                }
                showMessage("Lote activo: ${lot.tank}.")
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                showError(e)
            }
        }
    }

    fun updateLot(lot: Lot, tank: String, note: String, onDone: () -> Unit) {
        val current = session ?: return
        if (!current.role.canEditLots) {
            showMessage("Tu rol no permite editar lotes.")
            return
        }
        MeasurementValidator.validateTankName(tank)?.let {
            showMessage(it)
            return
        }
        if (note.trim().length > MAX_NOTE) {
            showMessage("La observación no puede superar $MAX_NOTE caracteres.")
            return
        }
        write(
            done = "Lote actualizado.",
            failure = "No se pudo actualizar el lote",
            onDone = onDone
        ) { repository.updateLot(lot.id, tank.trim(), note.trim()) }
    }

    // ---------------------------------------------------------------- mediciones

    /**
     * Registra la lectura que se ve en pantalla como medicion del lote activo.
     * Antes de guardar valida: que haya lote, que la lectura sea valida, reciente, estable y no
     * repetida, y que su dispositivo este registrado. Si algo falla, explica el motivo y no guarda.
     */
    fun recordMeasurement(reading: MilkQualityReading?, stability: StabilityState) {
        val current = session ?: return
        val st = _state.value
        if (reading == null) {
            showMessage("Aún no hay una lectura para registrar.")
            return
        }
        val lotId = st.currentLotId
        if (lotId == null) {
            showMessage("Primero elige o crea el lote de hoy.")
            return
        }
        val check = MeasurementValidator.validate(reading, System.currentTimeMillis(), st.lastRecordedTimestamp)
        if (!check.isValid) {
            showMessage(check.firstError ?: "La lectura no es válida.")
            return
        }
        if (!stability.isStable) {
            showMessage("Espera a que la lectura se estabilice (${stability.samples}/${stability.required}).")
            return
        }
        val deviceId = RecordRules.deviceIdFor(reading.source)
        if (deviceId in st.missingDevices) {
            showMessage("El dispositivo \"$deviceId\" no está registrado. El administrador debe ingresar a la app para registrarlo.")
            return
        }

        val lotName = st.lotName(lotId)
        val kind = if (reading.isSimulated) "Medición simulada" else "Medición"
        val result = repository.recordMeasurement(
            reading = reading,
            lotId = lotId,
            session = current,
            ranges = st.ranges,
            onSynced = { showMessage("$kind confirmada en la nube ($lotName).") },
            onError = { e -> showMessage("La nube rechazó la medición: " + e.toUserMessage()) }
        )
        val alerts = if (result.alertCount > 0) " Se generaron ${result.alertCount} alerta(s)." else ""
        val where = if (st.online) "Enviando al servidor…" else "Sin conexión: quedó guardada en el teléfono y se enviará al volver la red."
        _state.update { it.copy(lastRecordedTimestamp = reading.timestamp) }
        showMessage("$kind registrada ($lotName).$alerts $where")
    }

    // ------------------------------------------------------------------ mensajes

    /**
     * Ejecuta una escritura en Firestore. Con internet espera la confirmacion del servidor;
     * sin internet no bloquea la pantalla: Firestore guarda el cambio en el telefono y lo envia
     * al volver la red (si el servidor lo rechaza despues, se avisa).
     */
    private fun write(done: String, failure: String, onDone: () -> Unit, block: suspend () -> Unit) {
        if (!_state.value.online) {
            viewModelScope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    showMessage("$failure: " + e.toUserMessage())
                }
            }
            showMessage("$done Sin conexión: se enviará al volver la red.")
            onDone()
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(saving = false) }
                showMessage(done)
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                showMessage("$failure: " + e.toUserMessage())
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    private fun showError(e: Exception) {
        val text = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Permiso denegado al leer o guardar datos. Revisa tu rol y que las reglas de Firestore estén publicadas."
        } else {
            e.toUserMessage()
        }
        showMessage(text)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RecordsViewModel(container.recordsRepository, container.connectivity)
            }
        }
    }
}
