package com.example.myapplication.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.components.NoticeBanner
import com.example.myapplication.ui.components.OfflineBanner
import com.example.myapplication.ui.components.SavedMeasurementRow
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.TrendChart
import com.example.myapplication.ui.components.displayName
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.StatusTone
import com.example.myapplication.viewmodel.OriginFilter
import com.example.myapplication.viewmodel.RecordsViewModel
import kotlinx.coroutines.delay

private const val RECENT_ON_PANEL = 3

/**
 * Panel principal: lo que pasa hoy (mediciones, % conforme, alertas pendientes, ultima medicion),
 * tendencia de 7 dias y accesos directos. Todo sale de Firestore; los datos simulados se marcan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: RecordsViewModel,
    session: UserSession,
    onMeasure: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.dashboard
    val status = AppTheme.status

    // Los indicadores de "hoy" y "hace X min" se recalculan cada minuto.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
            viewModel.refreshDashboard()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { ScreenTitle("Calidad de Leche", "Hola, ${session.name.substringBefore(' ')} · ${session.role.label}") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.online) {
                item { OfflineBanner(state.pendingSyncCount) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OriginFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.dashboardOrigin == f,
                            onClick = { viewModel.setDashboardOrigin(f) },
                            label = { Text(f.label) }
                        )
                    }
                }
            }

            if (state.recentLoading && summary == null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            } else {
                item {
                    val today = summary?.todayCount ?: 0
                    val percent = summary?.todayCompliantPercent
                    val pending = state.pendingAlerts.size
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard(
                                label = "Mediciones hoy",
                                value = today.toString(),
                                icon = Icons.Outlined.Assessment,
                                tone = status.info,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                label = "Conformes hoy",
                                value = percent?.let { "%.0f %%".format(it) } ?: "—",
                                icon = Icons.Outlined.CheckCircle,
                                tone = when {
                                    percent == null -> status.info
                                    percent >= 90 -> status.good
                                    percent >= 60 -> status.warning
                                    else -> status.alert
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard(
                                label = "Alertas pendientes",
                                value = pending.toString(),
                                icon = Icons.Outlined.NotificationsActive,
                                tone = if (pending == 0) status.good else status.alert,
                                onClick = onOpenAlerts,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                label = "Última medición",
                                value = summary?.lastMeasurementMillis?.let { timeAgo(it, now) } ?: "—",
                                icon = Icons.Outlined.Schedule,
                                tone = status.info,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Lote activo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    state.currentLot?.displayName() ?: "Sin lote elegido",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(onClick = onMeasure, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Filled.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Medir")
                            }
                        }
                    }
                }

                // "Mi dia" (trabajador): sus propias mediciones, lo que le toca medir y sus revisiones pedidas.
                if (!session.role.canViewAllHistory) item {
                    MyDayCard(state = state, session = session, now = now)
                }

                // Supervision (encargado y administrador): estado por lote, lecheros, comparativas y resumen.
                if (session.role.canViewAllHistory) item {
                    SupervisionPanel(state = state, session = session, now = now, onOpenAlerts = onOpenAlerts)
                }

                val days = summary?.days.orEmpty()
                if (days.isNotEmpty()) item {
                    TrendChart(
                        title = "pH promedio diario",
                        unit = "",
                        labels = days.map { it.label },
                        values = days.map { it.avgPh },
                        bandMin = state.ranges.phMin.toDouble(),
                        bandMax = state.ranges.phMax.toDouble(),
                        decimals = 2
                    )
                }
                if (days.isNotEmpty()) item {
                    TrendChart(
                        title = "Temperatura promedio diaria",
                        unit = "°C",
                        labels = days.map { it.label },
                        values = days.map { it.avgTemperature },
                        bandMin = null,
                        bandMax = state.ranges.tempMax.toDouble(),
                        decimals = 1
                    )
                }

                val latest = state.recentValid.filter { state.dashboardOrigin.matches(it.isSimulated) }.take(RECENT_ON_PANEL)
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Últimas mediciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenHistory) { Text("Ver historial") }
                    }
                }
                if (latest.isEmpty()) {
                    item {
                        Text(
                            "No hay mediciones en los últimos 7 días. Ve a Medir para registrar la primera.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(latest, key = { it.id }) { m ->
                        SavedMeasurementRow(
                            measurement = m,
                            lotName = state.lotName(m.lotId),
                            showMilker = session.role.canViewAllHistory
                        )
                    }
                }
            }
        }
    }
}

/** Indicador del panel: icono, valor grande y etiqueta. Si tiene accion, se puede tocar. */
@Composable
private fun KpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container),
        modifier = modifier
            .height(104.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(22.dp))
            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = tone.content, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelMedium, color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "hace 5 min", "hace 2 h", "hace 3 d". */
private fun timeAgo(millis: Long, now: Long): String {
    val minutes = ((now - millis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "ahora"
        minutes < 60 -> "hace $minutes min"
        minutes < 24 * 60 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / (24 * 60)} d"
    }
}
