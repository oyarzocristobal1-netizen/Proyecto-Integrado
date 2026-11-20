package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.ConnectivityObserver
import com.example.myapplication.data.DeletionRepository
import com.example.myapplication.data.HISTORY_PAGE_SIZE
import com.example.myapplication.data.Lot
import com.example.myapplication.data.MilkAlert
import com.example.myapplication.data.RecordsRepository
import com.example.myapplication.data.Tank
import com.example.myapplication.data.TankResult
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.data.toUserMessage
import com.example.myapplication.model.CrudRules
import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.DashboardStats
import com.example.myapplication.model.DashboardSummary
import com.example.myapplication.model.MeasurementPoint
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.QualityRangesHolder
import com.example.myapplication.model.RecordRules
import com.example.myapplication.model.SaleDecision
import com.example.myapplication.model.SaleRelease
import com.example.myapplication.model.analysis.VerdictLevel
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

/** Filtro de alertas por severidad. */
enum class SeverityFilter(val label: String, val severity: AlertSeverity?) {
    ALL("Todas", null),
    HIGH("Alta", AlertSeverity.ALTA),
    MEDIUM("Media", AlertSeverity.MEDIA)
}

data class RecordsUiState(
    /** El telefono tiene internet (si no, lo registrado se guarda y se envia despues). */
    val online: Boolean = true,
    val lots: List<Lot> = emptyList(),
    /** Catalogo de estanques (activos e inactivos). */
    val tanks: List<Tank> = emptyList(),
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
    val alertsSeverity: SeverityFilter = SeverityFilter.ALL,
    /** Lote por el que se filtran las alertas (null = todos). */
    val alertsLotFilter: String? = null,
    /** Alertas marcadas para atenderlas juntas. */
    val selectedAlertIds: Set<String> = emptySet(),

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
    /** Lotes de hoy donde todavia se puede medir (los cerrados no admiten mediciones). */
    val todayLots: List<Lot> get() = lots.filter { isToday(it.date) && !it.closed }
    val activeTanks: List<Tank> get() = tanks.filter { it.active }

    /** Mediciones validas para analisis, panel y reportes: las anuladas se muestran pero no cuentan. */
    val historyValid: List<SavedMeasurement> get() = history.filterNot { it.annulled }
    val recentValid: List<SavedMeasurement> get() = recent.filterNot { it.annulled }
    fun lotName(lotId: String): String = lots.firstOrNull { it.id == lotId }?.tank ?: lotId

    /** Historial con los filtros de origen y semaforo aplicados a las mediciones cargadas. */
    val filteredHistory: List<SavedMeasurement>
        get() = history.filter { m ->
            historyOrigin.matches(m.isSimulated) && (historyStatus.status == null || m.status == historyStatus.status)
        }

    /** Alertas de la lista con los filtros de severidad y lote aplicados. */
    val filteredAlerts: List<MilkAlert>
        get() = alerts.filter { a ->
            (alertsSeverity.severity == null || a.severity == alertsSeverity.severity) &&
                (alertsLotFilter == null || a.lotId == alertsLotFilter)
        }

    /** Alertas marcadas que siguen visibles y pendientes (las que se atenderian juntas). */
    val selectedPendingAlerts: List<MilkAlert>
        get() = filteredAlerts.filter { !it.attended && it.id in selectedAlertIds }

    /** La medicion de una alerta, si esta entre las cargadas (historial o ultimos 7 dias). */
    fun measurementOf(alert: MilkAlert): SavedMeasurement? =
        recent.firstOrNull { it.id == alert.measurementId } ?: history.firstOrNull { it.id == alert.measurementId }

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
    connectivity: ConnectivityObserver,
    private val deletions: DeletionRepository
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
    private var tanksListener: ListenerRegistration? = null

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
                    val current = st.currentLotId?.takeIf { id -> lots.any { it.id == id && !it.closed } }
                        ?: lots.firstOrNull { isToday(it.date) && !it.closed }?.id
                    st.copy(lots = lots, currentLotId = current)
                }
            },
            onError = ::showError
        )
        tanksListener = repository.listenTanks(
            onChange = { list -> _state.update { it.copy(tanks = list) } },
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
        listOf(lotsListener, historyListener, recentListener, pendingAlertsListener, alertsListener, rangesListener,
            tanksListener)
            .forEach { it?.remove() }
        lotsListener = null
        historyListener = null
        recentListener = null
        pendingAlertsListener = null
        alertsListener = null
        rangesListener = null
        tanksListener = null
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
        val points = st.recentValid
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

    fun setAlertsSeverity(filter: SeverityFilter) = _state.update { it.copy(alertsSeverity = filter, selectedAlertIds = emptySet()) }

    fun setAlertsLotFilter(lotId: String?) = _state.update { it.copy(alertsLotFilter = lotId, selectedAlertIds = emptySet()) }

    fun toggleAlertSelected(alertId: String) = _state.update {
        it.copy(selectedAlertIds = if (alertId in it.selectedAlertIds) it.selectedAlertIds - alertId else it.selectedAlertIds + alertId)
    }

    /** Marca todas las alertas pendientes visibles (o las desmarca si ya estaban todas marcadas). */
    fun toggleSelectAllPending() = _state.update { st ->
        val pending = st.filteredAlerts.filter { !it.attended }.map { it.id }.toSet()
        st.copy(selectedAlertIds = if (pending.isNotEmpty() && st.selectedAlertIds.containsAll(pending)) emptySet() else pending)
    }

    /** Atiende varias alertas a la vez con la misma nota opcional (encargado y administrador). */
    fun markSelectedAlertsAttended(note: String, onDone: () -> Unit = {}) {
        val current = session ?: return
        if (!current.role.canAttendAlerts) return
        val ids = _state.value.selectedPendingAlerts.map { it.id }
        if (ids.isEmpty()) {
            showMessage("Marca al menos una alerta pendiente.")
            return
        }
        CrudRules.validateAlertNote(note)?.let {
            showMessage(it)
            return
        }
        write(
            done = if (ids.size == 1) "Alerta marcada como atendida." else "${ids.size} alertas marcadas como atendidas.",
            failure = "No se pudieron marcar las alertas",
            onDone = {
                _state.update { it.copy(selectedAlertIds = emptySet()) }
                onDone()
            }
        ) { repository.markAlertsAttended(ids, note, current) }
    }

    /** Solo encargado y administrador (lo exigen las reglas). La nota es opcional. */
    fun markAlertAttended(alert: MilkAlert, note: String, onDone: () -> Unit = {}) {
        val current = session ?: return
        if (!current.role.canAttendAlerts || alert.attended) return
        CrudRules.validateAlertNote(note)?.let {
            showMessage(it)
            return
        }
        write(
            done = "Alerta marcada como atendida.",
            failure = "No se pudo marcar la alerta",
            onDone = onDone
        ) { repository.markAlertAttended(alert.id, note, current) }
    }

    /** Corrige la nota de una alerta ya atendida. */
    fun updateAlertNote(alert: MilkAlert, note: String, onDone: () -> Unit = {}) {
        val current = session ?: return
        if (!current.role.canAttendAlerts || !alert.attended) return
        CrudRules.validateAlertNote(note)?.let {
            showMessage(it)
            return
        }
        write(
            done = "Nota actualizada.",
            failure = "No se pudo guardar la nota",
            onDone = onDone
        ) { repository.updateAlertNote(alert.id, note) }
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
        if (_state.value.lots.firstOrNull { it.id == lotId }?.closed == true) {
            showMessage("Ese lote está cerrado: no admite mediciones nuevas.")
            return
        }
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
                if (lot.closed) {
                    _state.update { it.copy(saving = false) }
                    showMessage("El lote de hoy de ${lot.tank} está cerrado. Pide al encargado que lo reabra para seguir midiendo.")
                    return@launch
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

    /**
     * El encargado decide si el lote es apto o no apto para venta. El analisis solo sugiere: si decide
     * liberar un lote que el analisis marco para revisar (o con una medicion atrasada), debe escribir por que.
     */
    fun setLotSale(
        lot: Lot,
        decision: SaleDecision?,
        note: String,
        verdict: VerdictLevel?,
        measurementCount: Int,
        overdue: Boolean,
        onDone: () -> Unit
    ) {
        val current = session ?: return
        if (!current.role.canEditLots) {
            showMessage("Tu rol no permite liberar lotes para venta.")
            return
        }
        SaleRelease.validate(decision, verdict, note, measurementCount, overdue)?.let {
            showMessage(it)
            return
        }
        val chosen = decision ?: return
        write(
            done = "Lote marcado: ${chosen.label.lowercase()}.",
            failure = "No se pudo guardar la decisión",
            onDone = onDone
        ) { repository.setLotSale(lot.id, chosen, note, current) }
    }

    /** Cierra o reabre un lote (encargado y administrador). Un lote cerrado no admite mediciones. */
    fun setLotClosed(lot: Lot, closed: Boolean, onDone: () -> Unit = {}) {
        val current = session ?: return
        if (!current.role.canManageTanks) {
            showMessage("Tu rol no permite cerrar ni reabrir lotes.")
            return
        }
        if (lot.closed == closed) return
        write(
            done = if (closed) "Lote cerrado: ya no admite mediciones." else "Lote reabierto.",
            failure = if (closed) "No se pudo cerrar el lote" else "No se pudo reabrir el lote",
            onDone = onDone
        ) { repository.setLotClosed(lot.id, closed, current) }
    }

    // ----------------------------------------------------------------- estanques

    /** Crea un estanque en el catalogo (encargado y administrador). Requiere conexion. */
    fun createTank(name: String, onDone: () -> Unit) {
        val current = session ?: return
        if (!current.role.canManageTanks) {
            showMessage("Tu rol no permite administrar estanques.")
            return
        }
        MeasurementValidator.validateTankName(name)?.let {
            showMessage(it)
            return
        }
        if (!_state.value.online) {
            showMessage("Sin conexión: conéctate a internet para crear un estanque.")
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val result = repository.createTank(name, current)
                _state.update { it.copy(saving = false) }
                when (result) {
                    TankResult.CREATED -> showMessage("Estanque creado.")
                    TankResult.REACTIVATED -> showMessage("Ese estanque estaba desactivado: se activó de nuevo.")
                    TankResult.ALREADY_EXISTS -> {
                        showMessage("Ya existe un estanque con ese nombre.")
                        return@launch
                    }
                }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                showMessage("No se pudo crear el estanque: " + e.toUserMessage())
            }
        }
    }

    /** Cambia el nombre de un estanque. Los lotes ya creados conservan el nombre original. */
    fun renameTank(tank: Tank, name: String, onDone: () -> Unit) {
        val current = session ?: return
        if (!current.role.canManageTanks) {
            showMessage("Tu rol no permite administrar estanques.")
            return
        }
        MeasurementValidator.validateTankName(name)?.let {
            showMessage(it)
            return
        }
        write(
            done = "Estanque actualizado.",
            failure = "No se pudo actualizar el estanque",
            onDone = onDone
        ) { repository.updateTank(tank.id, name, tank.active) }
    }

    /** Desactiva un estanque (deja de ofrecerse al medir) o lo activa de nuevo. No se borra. */
    fun setTankActive(tank: Tank, active: Boolean) {
        val current = session ?: return
        if (!current.role.canManageTanks) {
            showMessage("Tu rol no permite administrar estanques.")
            return
        }
        if (tank.active == active) return
        write(
            done = if (active) "Estanque activado." else "Estanque desactivado: ya no se ofrece al medir.",
            failure = "No se pudo cambiar el estanque",
            onDone = {}
        ) { repository.updateTank(tank.id, tank.name, active) }
    }

    // --------------------------------------------------------------- eliminaciones

    /**
     * Eliminar de verdad (solo administrador): ultimo recurso, queda anotado en la bitacora.
     * Lo normal es desactivar, anular o cerrar. Las reglas de Firestore tambien lo exigen.
     */
    private fun deletionAllowed(): UserSession? {
        val current = session ?: return null
        if (!current.role.canDeleteRecords) {
            showMessage("Solo el administrador puede eliminar registros.")
            return null
        }
        return current
    }

    fun deleteTank(tank: Tank, onDone: () -> Unit = {}) {
        val current = deletionAllowed() ?: return
        write(
            done = "Estanque eliminado.",
            failure = "No se pudo eliminar el estanque",
            onDone = onDone
        ) { deletions.deleteTank(tank, current) }
    }

    fun deleteAlert(alert: MilkAlert, onDone: () -> Unit = {}) {
        val current = deletionAllowed() ?: return
        write(
            done = "Alerta eliminada.",
            failure = "No se pudo eliminar la alerta",
            onDone = onDone
        ) { deletions.deleteAlert(alert, current) }
    }

    /** Elimina la medicion y sus alertas. */
    fun deleteMeasurement(measurement: SavedMeasurement, onDone: () -> Unit = {}) {
        val current = deletionAllowed() ?: return
        if (measurement.pendingSync) {
            showMessage("Esa medición aún no llegó al servidor: espera a que se sincronice para eliminarla.")
            return
        }
        val lotName = _state.value.lotName(measurement.lotId)
        write(
            done = "Medición eliminada junto con sus alertas.",
            failure = "No se pudo eliminar la medición",
            onDone = onDone
        ) { deletions.deleteMeasurement(measurement, lotName, current) }
    }

    /** Elimina el lote con todas sus mediciones y alertas. Necesita conexion (lee lo que va a borrar). */
    fun deleteLot(lot: Lot, onDone: () -> Unit = {}) {
        val current = deletionAllowed() ?: return
        if (!_state.value.online) {
            showMessage("Sin conexión: conéctate a internet para eliminar un lote con sus mediciones.")
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val count = deletions.deleteLot(lot, current)
                val filterWasThisLot = _state.value.historyLotFilter == lot.id
                _state.update { st ->
                    st.copy(
                        saving = false,
                        historyLotFilter = st.historyLotFilter?.takeIf { it != lot.id },
                        currentLotId = st.currentLotId?.takeIf { it != lot.id }
                    )
                }
                if (filterWasThisLot) listenHistory()
                showMessage("Lote eliminado con $count medición(es).")
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                showMessage("No se pudo eliminar el lote: " + e.toUserMessage())
            }
        }
    }

    // ---------------------------------------------------------------- mediciones

    /**
     * Anula una medicion registrada por error (encargado y administrador). No se borra: queda
     * marcada con el motivo, quien la anulo y cuando, y deja de contar en analisis y reportes.
     */
    fun annulMeasurement(measurement: SavedMeasurement, reason: String, onDone: () -> Unit) {
        val current = session ?: return
        if (!current.role.canAnnulMeasurements) {
            showMessage("Tu rol no permite anular mediciones.")
            return
        }
        if (measurement.annulled) return
        if (measurement.pendingSync) {
            showMessage("Esa medición aún no llegó al servidor: espera a que se sincronice para anularla.")
            return
        }
        CrudRules.validateAnnulReason(reason)?.let {
            showMessage(it)
            return
        }
        write(
            done = "Medición anulada. Queda en el historial con su motivo.",
            failure = "No se pudo anular la medición",
            onDone = onDone
        ) { repository.annulMeasurement(measurement.id, reason, current) }
    }

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
        if (st.currentLot?.closed == true) {
            showMessage("El lote está cerrado: reábrelo o elige otro para registrar.")
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
                RecordsViewModel(container.recordsRepository, container.connectivity, container.deletionRepository)
            }
        }
    }
}
