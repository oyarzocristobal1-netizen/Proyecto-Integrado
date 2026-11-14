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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.SimulatedBadge
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.RecordsViewModel
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
    var confirming by remember { mutableStateOf<MilkAlert?>(null) }

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

                state.alerts.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.NotificationsNone,
                        title = if (state.alertsOnlyPending) "Sin alertas pendientes" else "Sin alertas",
                        message = "Las alertas se crean solas cuando una medición registrada queda fuera de los rangos de calidad."
                    )
                }

                else -> items(state.alerts, key = { it.id }) { alert ->
                    AlertRow(
                        alert = alert,
                        lotName = state.lotName(alert.lotId),
                        dateText = dateFormat.format(alert.date),
                        canAttend = session.role.canAttendAlerts,
                        onAttend = { confirming = alert }
                    )
                }
            }
        }
    }

    confirming?.let { alert ->
        ConfirmDialog(
            title = "¿Marcar como atendida?",
            text = "\"${alert.message}\"\n\nConfirma que revisaste la leche y tomaste una acción. " +
                "Una alerta atendida no se puede volver a marcar como pendiente.",
            confirmLabel = "Marcar atendida",
            onConfirm = {
                viewModel.markAlertAttended(alert)
                confirming = null
            },
            onDismiss = { confirming = null }
        )
    }
}

@Composable
private fun AlertRow(
    alert: MilkAlert,
    lotName: String,
    dateText: String,
    canAttend: Boolean,
    onAttend: () -> Unit
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
                Icon(icon, contentDescription = null, tint = content)
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    modifier = Modifier.weight(1f)
                )
            }
            if (alert.isSimulated) SimulatedBadge()
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
            }
        }
    }
}
