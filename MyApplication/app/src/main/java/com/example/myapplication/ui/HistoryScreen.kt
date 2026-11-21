package com.example.myapplication.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.model.CrudRules
import com.example.myapplication.model.UserSession
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.analysis.LotAnalyzer
import com.example.myapplication.ui.components.HistoricalAnalysisSection
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.OfflineBanner
import com.example.myapplication.ui.components.SavedMeasurementRow
import com.example.myapplication.ui.components.TextInputDialog
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.displayName
import com.example.myapplication.viewmodel.OriginFilter
import com.example.myapplication.viewmodel.RecordsViewModel
import com.example.myapplication.viewmodel.StatusFilter

/**
 * Historial de mediciones guardadas (CU4), en tiempo real y por paginas:
 * encargado y administrador ven todas (filtro por lote); el lechero, solo las propias.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: RecordsViewModel,
    session: UserSession,
    onMeasure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canFilterLots = session.role.canViewAllHistory
    val shown = state.filteredHistory
    var annulling by remember { mutableStateOf<SavedMeasurement?>(null) }
    var deleting by remember { mutableStateOf<SavedMeasurement?>(null) }

    // Si el filtro por origen quedo oculto, no puede quedar activo.
    LaunchedEffect(state.hasMixedOrigins, state.historyOrigin) {
        if (!state.hasMixedOrigins && state.historyOrigin != OriginFilter.ALL) {
            viewModel.setHistoryOrigin(OriginFilter.ALL)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    ScreenTitle(
                        if (canFilterLots) "Historial" else "Mis mediciones",
                        "${shown.size} de ${state.history.size} cargadas"
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.online) {
                item { OfflineBanner(state.pendingSyncCount) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canFilterLots) {
                        LotFilter(
                            label = state.lots.firstOrNull { it.id == state.historyLotFilter }?.displayName() ?: "Todos los lotes",
                            options = state.lots.map { it.id to it.displayName() },
                            onSelect = viewModel::setHistoryLotFilter
                        )
                    }
                    // El filtro por origen solo aparece si hay datos del sensor y simulados a la vez.
                    if (state.hasMixedOrigins) ChipRow {
                        OriginFilter.entries.forEach { f ->
                            FilterChip(
                                selected = state.historyOrigin == f,
                                onClick = { viewModel.setHistoryOrigin(f) },
                                label = { Text(f.label) }
                            )
                        }
                    }
                    ChipRow {
                        StatusFilter.entries.forEach { f ->
                            FilterChip(
                                selected = state.historyStatus == f,
                                onClick = { viewModel.setHistoryStatus(f) },
                                label = { Text(f.label) }
                            )
                        }
                    }
                }
            }

            // El analisis resume cada lote completo: con un filtro de resultado u origen activo se oculta,
            // para que la lista filtrada quede a la vista justo debajo de los filtros.
            val filtering = state.historyStatus != StatusFilter.ALL || state.historyOrigin != OriginFilter.ALL
            if (canFilterLots && state.history.isNotEmpty() && !filtering) {
                item {
                    val lotAnalyses = remember(state.history, state.ranges) {
                        LotAnalyzer.analyze(state.historyValid.map { it.asAnalysisReading() }, state.ranges)
                    }
                    HistoricalAnalysisSection(items = lotAnalyses, lotName = { id -> state.lotName(id) })
                }
            }

            when {
                state.historyLoading && state.history.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                state.history.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = "Sin mediciones",
                        message = if (state.historyLotFilter != null) {
                            "Este lote todavía no tiene mediciones registradas."
                        } else {
                            "Aún no hay mediciones registradas."
                        },
                        actionLabel = "Ir a Medir",
                        onAction = onMeasure
                    )
                }

                shown.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = "Sin resultados",
                        message = "Ninguna medición cargada cumple los filtros elegidos.",
                        actionLabel = "Quitar filtros",
                        onAction = {
                            viewModel.setHistoryOrigin(OriginFilter.ALL)
                            viewModel.setHistoryStatus(StatusFilter.ALL)
                        }
                    )
                }

                else -> items(shown, key = { it.id }) { m ->
                    SavedMeasurementRow(
                        measurement = m,
                        lotName = state.lotName(m.lotId),
                        showMilker = canFilterLots,
                        onAnnul = if (session.role.canAnnulMeasurements) {
                            { annulling = m }
                        } else {
                            null
                        },
                        onDelete = if (session.role.canDeleteRecords) {
                            { deleting = m }
                        } else {
                            null
                        }
                    )
                }
            }

            if (state.historyHasMore) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadMoreHistory,
                        enabled = !state.historyLoading,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (state.historyLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Cargar mediciones más antiguas")
                        }
                    }
                }
            }
        }
    }

    deleting?.let { m ->
        ConfirmDialog(
            title = "¿Eliminar la medición?",
            text = "Se elimina la medición y sus alertas. No se puede deshacer y deja de aparecer en el historial y " +
                "los reportes. Queda anotado en la bitácora. Si solo se registró por error, usa Anular: queda " +
                "guardada con su motivo.",
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = {
                viewModel.deleteMeasurement(m)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }

    annulling?.let { m ->
        TextInputDialog(
            title = "Anular medición",
            description = "La medición no se borra: queda en el historial marcada como anulada, con tu nombre y el motivo, " +
                "y deja de contar en el análisis, el panel y los reportes. No se puede deshacer.",
            label = "Motivo",
            placeholder = "Ej.: sensor mal puesto, se midió otro estanque",
            initial = "",
            maxLength = CrudRules.ANNUL_REASON_MAX,
            confirmLabel = "Anular",
            saving = state.saving,
            validate = CrudRules::validateAnnulReason,
            stateKey = m.id,
            onConfirm = { reason -> viewModel.annulMeasurement(m, reason) { annulling = null } },
            onDismiss = { annulling = null }
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** Selector de lote (null = todos). */
@Composable
private fun LotFilter(label: String, options: List<Pair<String, String>>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Filtrar por lote")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todos los lotes") },
                onClick = {
                    expanded = false
                    onSelect(null)
                }
            )
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}
