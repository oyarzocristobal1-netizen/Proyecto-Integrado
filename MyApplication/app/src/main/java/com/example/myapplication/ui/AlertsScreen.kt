package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.MilkAlert
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.CrudRules
import com.example.myapplication.model.ReviewRules
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.TextInputDialog
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.SavedMeasurementRow
import com.example.myapplication.ui.components.SimulatedBadge
import com.example.myapplication.ui.components.displayName
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.RecordsViewModel
import com.example.myapplication.viewmodel.SeverityFilter
import java.text.SimpleDateFormat
import java.util.Locale

/** Alertas generadas por valores fuera de rango (CU3). Todos las ven; encargado y admin las atienden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: RecordsViewModel,
    session: UserSession,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var attending by remember { mutableStateOf<MilkAlert?>(null) }
    var editingNote by remember { mutableStateOf<MilkAlert?>(null) }
    var deleting by remember { mutableStateOf<MilkAlert?>(null) }
    var bulkAttending by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf<SavedMeasurement?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var lotMenuOpen by remember { mutableStateOf(false) }
    val visible = state.filteredAlerts
    val pendingVisible = visible.filter { !it.attended }
    val selectedCount = state.selectedPendingAlerts.size
    val filtersActive = state.alertsSeverity != SeverityFilter.ALL || state.alertsLotFilter != null

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { ScreenTitle("Alertas", "${state.pendingAlerts.size} pendiente(s)") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.alertsOnlyPending,
                        onClick = { viewModel.setAlertsOnlyPending(true) },
                        label = { Text("Pendientes (${state.pendingAlerts.size})") }
                    )
                    FilterChip(
                        selected = !state.alertsOnlyPending,
                        onClick = { viewModel.setAlertsOnlyPending(false) },
                        label = { Text("Todas") }
                    )
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeverityFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.alertsSeverity == filter,
                            onClick = { viewModel.setAlertsSeverity(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
            item {
                Box {
                    FilterChip(
                        selected = state.alertsLotFilter != null,
                        onClick = { lotMenuOpen = true },
                        label = {
                            Text(state.alertsLotFilter?.let { state.lotName(it) }?.let { "Lote: $it" } ?: "Todos los lotes")
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = lotMenuOpen, onDismissRequest = { lotMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Todos los lotes") },
                            onClick = {
                                viewModel.setAlertsLotFilter(null)
                                lotMenuOpen = false
                            }
                        )
                        state.lots.forEach { lot ->
                            DropdownMenuItem(
                                text = { Text(lot.displayName()) },
                                onClick = {
                                    viewModel.setAlertsLotFilter(lot.id)
                                    lotMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
            if (session.role.canAttendAlerts && state.pendingReviews.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Revisiones pedidas por los trabajadores (${state.pendingReviews.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        state.pendingReviews.forEach { m ->
                            SavedMeasurementRow(
                                measurement = m,
                                lotName = state.lotName(m.lotId),
                                showMilker = true,
                                onReview = { reviewing = m }
                            )
                        }
                    }
                }
            }
            if (session.role.canAttendAlerts && pendingVisible.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { viewModel.toggleSelectAllPending() }) {
                            Text(
                                if (selectedCount == pendingVisible.size) "Quitar selección"
                                else "Seleccionar pendientes (${pendingVisible.size})"
                            )
                        }
                        Box(modifier = Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = { bulkAttending = true },
                            enabled = selectedCount > 0
                        ) { Text("Atender ($selectedCount)") }
                    }
                }
            }
            if (!session.role.canAttendAlerts) {
                item {
                    Text(
                        "El encargado o el administrador marcan las alertas como atendidas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                state.alertsLoading && state.alerts.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                visible.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.NotificationsNone,
                        title = when {
                            filtersActive -> "Sin alertas con estos filtros"
                            state.alertsOnlyPending -> "Sin alertas pendientes"
                            else -> "Sin alertas"
                        },
                        message = if (filtersActive) {
                            "Prueba con otra severidad o con todos los lotes."
                        } else {
                            "Las alertas se crean solas cuando una medición registrada queda fuera de los rangos de calidad."
                        }
                    )
                }

                else -> items(visible, key = { it.id }) { alert ->
                    AlertRow(
                        alert = alert,
                        lotName = state.lotName(alert.lotId),
                        dateText = dateFormat.format(alert.date),
                        canAttend = session.role.canAttendAlerts,
                        dateFormat = dateFormat,
                        canDelete = session.role.canDeleteRecords,
                        selected = alert.id in state.selectedAlertIds,
                        onToggleSelected = { viewModel.toggleAlertSelected(alert.id) },
                        measurement = if (expandedId == alert.id) state.measurementOf(alert) else null,
                        expanded = expandedId == alert.id,
                        onToggleExpanded = { expandedId = if (expandedId == alert.id) null else alert.id },
                        onAttend = { attending = alert },
                        onEditNote = { editingNote = alert },
                        onDelete = { deleting = alert }
                    )
                }
            }
        }
    }

    deleting?.let { alert ->
        ConfirmDialog(
            title = "¿Eliminar la alerta?",
            text = "\"${alert.message}\"\n\nSe elimina solo la alerta; la medición se conserva. No se puede deshacer y " +
                "queda anotado en la bitácora.",
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = {
                viewModel.deleteAlert(alert)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }

    attending?.let { alert ->
        TextInputDialog(
            title = "Marcar como atendida",
            description = "\"${alert.message}\"\n\nConfirma que revisaste la leche y tomaste una acción. " +
                "Puedes anotar qué se hizo (opcional). Una alerta atendida no vuelve a quedar pendiente.",
            label = "Nota (opcional)",
            placeholder = "Ej.: se enfrió el estanque y se volvió a medir",
            initial = "",
            maxLength = CrudRules.ALERT_NOTE_MAX,
            confirmLabel = "Marcar atendida",
            saving = state.saving,
            validate = CrudRules::validateAlertNote,
            stateKey = alert.id,
            onConfirm = { note -> viewModel.markAlertAttended(alert, note) { attending = null } },
            onDismiss = { attending = null }
        )
    }

    reviewing?.let { m ->
        TextInputDialog(
            title = "Marcar revisión como hecha",
            description = "Medición de ${m.milkerName.ifBlank { "un trabajador" }} en ${state.lotName(m.lotId)}." +
                (if (m.note.isNotBlank()) "\nNota del trabajador: ${m.note}" else "") +
                "\n\nConfirma que revisaste esta medición. Puedes anotar qué hiciste (opcional).",
            label = "Nota (opcional)",
            placeholder = "Ej.: se revisó la leche y se separó el lote",
            initial = "",
            maxLength = ReviewRules.NOTE_MAX,
            confirmLabel = "Marcar revisada",
            saving = state.saving,
            validate = ReviewRules::validateNote,
            stateKey = m.id + "-revision",
            onConfirm = { note -> viewModel.resolveReview(m, note) { reviewing = null } },
            onDismiss = { reviewing = null }
        )
    }

    if (bulkAttending) {
        TextInputDialog(
            title = "Atender $selectedCount alerta(s)",
            description = "Confirma que revisaste la leche de estas alertas y tomaste una acción. " +
                "La misma nota (opcional) se guarda en todas.",
            label = "Nota (opcional)",
            placeholder = "Ej.: se enfrió el estanque y se volvió a medir",
            initial = "",
            maxLength = CrudRules.ALERT_NOTE_MAX,
            confirmLabel = "Atender",
            saving = state.saving,
            validate = CrudRules::validateAlertNote,
            stateKey = "atencion-masiva",
            onConfirm = { note -> viewModel.markSelectedAlertsAttended(note) { bulkAttending = false } },
            onDismiss = { bulkAttending = false }
        )
    }

    editingNote?.let { alert ->
        TextInputDialog(
            title = "Nota de la alerta",
            description = "Corrige lo que se anotó al atender esta alerta.",
            label = "Nota",
            initial = alert.note,
            maxLength = CrudRules.ALERT_NOTE_MAX,
            confirmLabel = "Guardar",
            saving = state.saving,
            validate = CrudRules::validateAlertNote,
            stateKey = alert.id + "-nota",
            onConfirm = { note -> viewModel.updateAlertNote(alert, note) { editingNote = null } },
            onDismiss = { editingNote = null }
        )
    }
}

@Composable
private fun AlertRow(
    alert: MilkAlert,
    lotName: String,
    dateText: String,
    canAttend: Boolean,
    canDelete: Boolean,
    dateFormat: SimpleDateFormat,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    measurement: SavedMeasurement?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onAttend: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val status = AppTheme.status
    val (container, content) = when {
        alert.attended -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurfaceVariant
        alert.severity == AlertSeverity.ALTA -> status.alert.container to status.alert.content
        else -> status.warning.container to status.warning.content
    }
    val icon = when {
        alert.attended -> Icons.Default.CheckCircle
        alert.severity == AlertSeverity.ALTA -> Icons.Default.Error
        else -> Icons.Default.Warning
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canAttend && !alert.attended) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                }
                Icon(icon, contentDescription = null, tint = content)
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = buildString {
                    append(dateText)
                    append(" · ")
                    append(lotName)
                    if (alert.milkerName.isNotBlank()) append(" · ${alert.milkerName}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = content
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (alert.attended) "ATENDIDA" else "Severidad ${alert.severity.label.lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    modifier = Modifier.weight(1f)
                )
                if (!alert.attended && canAttend) {
                    FilledTonalButton(onClick = onAttend) { Text("Marcar atendida") }
                }
                if (alert.attended && canAttend) {
                    TextButton(onClick = onEditNote) { Text(if (alert.note.isBlank()) "Agregar nota" else "Editar nota") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "Ocultar medición" else "Ver medición", color = content)
                }
            }
            if (expanded) {
                HorizontalDivider(color = content.copy(alpha = 0.3f))
                if (measurement == null) {
                    Text(
                        "La medición de esta alerta ya no está disponible (se eliminó o es muy antigua).",
                        style = MaterialTheme.typography.bodySmall,
                        color = content
                    )
                } else {
                    Text(
                        text = buildString {
                            append("Resultado: ${measurement.title}")
                            append("\nTemperatura %.1f °C · pH %.2f".format(measurement.temperature, measurement.ph))
                            append("\nConductividad %.2f mS/cm · Turbidez %.0f %%".format(measurement.conductivity, measurement.turbidity))
                            append("\nTomada: ${dateFormat.format(measurement.dateTime)}")
                            if (measurement.annulled) append("\nMEDICIÓN ANULADA: ${measurement.annulReason}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = content
                    )
                }
            }
            if (canDelete) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                    Text("Eliminar", color = if (alert.attended) MaterialTheme.colorScheme.error else content)
                }
            }
            if (alert.attended) {
                val who = alert.attendedByName.ifBlank { null }
                val whenText = alert.attendedAt?.let { dateFormat.format(it) }
                if (who != null || whenText != null) {
                    Text(
                        text = "Atendida" + (who?.let { " por $it" } ?: "") + (whenText?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = content
                    )
                }
                if (alert.note.isNotBlank()) {
                    Text(
                        text = "Nota: ${alert.note}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = content
                    )
                }
            }
        }
    }
}
