package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.Lot
import com.example.myapplication.data.RecordsRepository
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.UserSession
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class RecordsUiState(
    val lots: List<Lot> = emptyList(),
    /** Lote donde se registran las mediciones. */
    val currentLotId: String? = null,
    /** Filtro del historial (null = todos los lotes). */
    val historyLotFilter: String? = null,
    val history: List<SavedMeasurement> = emptyList(),
    val historyLoading: Boolean = true,
    val saving: Boolean = false,
    /** Mensaje para mostrar una vez (snackbar). */
    val message: String? = null
) {
    val currentLot: Lot? get() = lots.firstOrNull { it.id == currentLotId }
    val todayLots: List<Lot> get() = lots.filter { isToday(it.date) }
    fun lotName(lotId: String): String = lots.firstOrNull { it.id == lotId }?.tank ?: lotId
}

private fun isToday(date: Date): Boolean {
    val a = Calendar.getInstance().apply { time = date }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

/** Lotes, registro de mediciones e historial guardado en Firestore, segun el rol de la sesion. */
class RecordsViewModel : ViewModel() {

    private val repository = RecordsRepository()

    private val _state = MutableStateFlow(RecordsUiState())
    val state: StateFlow<RecordsUiState> = _state.asStateFlow()

    private var session: UserSession? = null
    private var lotsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    /** Se llama al iniciar sesion. */
    fun start(newSession: UserSession) {
        if (session?.uid == newSession.uid && session?.role == newSession.role) return
        stop()
        session = newSession
        _state.value = RecordsUiState()
        lotsListener = repository.listenRecentLots(
            onChange = { lots ->
                _state.update { st ->
                    // Si no hay lote elegido, se toma el mas reciente de hoy.
                    val current = st.currentLotId?.takeIf { id -> lots.any { it.id == id } }
                        ?: lots.firstOrNull { isToday(it.date) }?.id
                    st.copy(lots = lots, currentLotId = current)
                }
            },
            onError = { e -> _state.update { it.copy(message = friendlyMessage(e)) } }
        )
        listenHistory()
    }

    /** Se llama al cerrar sesion: deja de escuchar Firestore antes de salir. */
    fun stop() {
        lotsListener?.remove()
        historyListener?.remove()
        lotsListener = null
        historyListener = null
        session = null
    }

    private fun listenHistory() {
        val current = session ?: return
        historyListener?.remove()
        _state.update { it.copy(historyLoading = true) }
        historyListener = repository.listenHistory(
            session = current,
            lotId = _state.value.historyLotFilter,
            onChange = { list -> _state.update { it.copy(history = list, historyLoading = false) } },
            onError = { e -> _state.update { it.copy(historyLoading = false, message = friendlyMessage(e)) } }
        )
    }

    fun setHistoryFilter(lotId: String?) {
        if (_state.value.historyLotFilter == lotId) return
        _state.update { it.copy(historyLotFilter = lotId) }
        listenHistory()
    }

    fun selectLot(lotId: String) {
        _state.update { it.copy(currentLotId = lotId) }
    }

    /** Usa el lote de hoy de ese estanque, o lo crea si no existe. */
    fun openTodayLot(tank: String, onDone: () -> Unit) {
        val current = session ?: return
        if (tank.isBlank()) {
            _state.update { it.copy(message = "Escribe el nombre del estanque (por ejemplo, Estanque 1).") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val lot = repository.getOrCreateTodayLot(tank, current)
                _state.update { st ->
                    val lots = if (st.lots.any { it.id == lot.id }) st.lots else listOf(lot) + st.lots
                    st.copy(lots = lots, currentLotId = lot.id, saving = false, message = "Lote activo: ${lot.tank}.")
                }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = friendlyMessage(e)) }
            }
        }
    }

    fun updateLot(lot: Lot, tank: String, note: String, onDone: () -> Unit) {
        if (tank.isBlank()) {
            _state.update { it.copy(message = "El estanque no puede quedar vacío.") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repository.updateLot(lot.id, tank, note)
                _state.update { it.copy(saving = false, message = "Lote actualizado.") }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = friendlyMessage(e)) }
            }
        }
    }

    /** Registra la lectura que se ve en pantalla como medicion del lote activo. */
    fun recordMeasurement(reading: MilkQualityReading) {
        val current = session ?: return
        val lotId = _state.value.currentLotId
        if (lotId == null) {
            _state.update { it.copy(message = "Primero elige o crea el lote de hoy.") }
            return
        }
        repository.saveMeasurement(reading, lotId, current) { e ->
            _state.update { it.copy(message = "No se pudo guardar la medición: " + friendlyMessage(e)) }
        }
        _state.update {
            it.copy(message = "Medición registrada en ${it.lotName(lotId)} (pH %.2f).".format(reading.ph))
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun friendlyMessage(e: Exception): String = when {
        e is FirebaseNetworkException -> "Sin conexión. Se guardará cuando vuelva la red."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permiso denegado. Revisa tu rol, que exista dispositivos/caja-01 y que las reglas estén publicadas."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "Falta un índice en Firestore para esta consulta (ver docs/configurar-firebase.md)."
        else -> "No se pudo completar la operación. Inténtalo de nuevo."
    }
}
