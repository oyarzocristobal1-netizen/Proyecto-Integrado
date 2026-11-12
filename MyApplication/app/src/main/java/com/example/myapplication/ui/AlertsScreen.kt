package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.MilkAlert
import com.example.myapplication.model.AlertSeverity
import com.example.myapplication.model.UserSession
import com.example.myapplication.viewmodel.RecordsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Alertas generadas por valores fuera de rango (CU3). Todos las ven; encargado y admin las atienden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: RecordsViewModel,
    session: UserSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Alertas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
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
            if (state.alerts.isEmpty()) {
                item {
                    Text(
                        if (state.alertsOnlyPending) "No hay alertas pendientes." else "Todavía no hay alertas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(state.alerts, key = { it.id }) { alert ->
                AlertRow(
                    alert = alert,
                    lotName = state.lotName(alert.lotId),
                    dateText = dateFormat.format(alert.date),
                    canAttend = session.role.canAttendAlerts,
                    onAttend = { viewModel.markAlertAttended(alert) }
                )
            }
        }
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
    val (container, content) = when {
        alert.attended -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurfaceVariant
        alert.severity == AlertSeverity.ALTA -> Color(0xFFFDE7E7) to Color(0xFF7A1212)
        else -> Color(0xFFFFF3D6) to Color(0xFF5C3B00)
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    TextButton(onClick = onAttend) { Text("Marcar atendida") }
                }
            }
        }
    }
}
