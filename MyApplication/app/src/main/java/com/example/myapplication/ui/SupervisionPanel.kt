package com.example.myapplication.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.DailyLotLine
import com.example.myapplication.model.DailySummary
import com.example.myapplication.model.GroupStats
import com.example.myapplication.model.SupervisionPoint
import com.example.myapplication.model.SupervisionStats
import com.example.myapplication.model.UserSession
import com.example.myapplication.model.analysis.LotAnalyzer
import com.example.myapplication.model.analysis.VerdictLevel
import com.example.myapplication.model.analysis.verdict
import com.example.myapplication.ui.components.SalePill
import com.example.myapplication.ui.components.SimulatedBadge
import com.example.myapplication.ui.components.StatusPill
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.StatusTone
import com.example.myapplication.viewmodel.RecordsUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val FARM = "Fundo San Carlos"

private enum class CompareBy(val label: String) { TANK("Por estanque"), MILKER("Por lechero") }

private fun SavedMeasurement.toPoint(tankName: String) = SupervisionPoint(
    milkerName = milkerName,
    lotId = tankName,
    epochMillis = dateTime.time,
    status = status,
    simulated = isSimulated,
    temperature = temperature,
    ph = ph,
    conductivity = conductivity,
    turbidity = turbidity
)

private fun startOfToday(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Panel de supervision del encargado: estado de cada lote de hoy, actividad de los lecheros,
 * comparativas de 7 dias y el resumen del dia para compartir. Todo sale de las mediciones cargadas;
 * lo simulado se marca siempre.
 */
@Composable
fun SupervisionPanel(
    state: RecordsUiState,
    session: UserSession,
    now: Long,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status = AppTheme.status
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val valid = state.recentValid
    val dayStart = remember(now) { startOfToday(now) }
    val todayMeasurements = valid.filter { it.dateTime.time >= dayStart }
    val analyses = remember(valid, state.ranges) {
        LotAnalyzer.analyze(valid.map { it.asAnalysisReading() }, state.ranges, maxLots = Int.MAX_VALUE)
            .associateBy { it.lotId }
    }
    val todayLots = state.lots.filter { it.date.time >= dayStart }
    val pendingByLot = state.pendingAlerts.groupingBy { it.lotId }.eachCount()
    var compareBy by rememberSaveable { mutableStateOf(CompareBy.TANK) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Supervisión de hoy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val lines = todayLots.map { lot ->
                        val count = todayMeasurements.count { it.lotId == lot.id }
                        val last = todayMeasurements.filter { it.lotId == lot.id }.maxOfOrNull { it.dateTime.time }
                        DailyLotLine(
                            tank = lot.tank,
                            count = count,
                            verdictLabel = analyses[lot.id]?.analysis?.verdict()?.level?.label,
                            lastTimeText = last?.let { timeFormat.format(Date(it)) },
                            pendingAlerts = pendingByLot[lot.id] ?: 0,
                            saleText = lot.sale?.let { d -> d.label + if (lot.saleNote.isNotBlank()) " (${lot.saleNote})" else "" }
                        )
                    }.filter { it.count > 0 }
                    val text = DailySummary.build(
                        farm = FARM,
                        date = Date(now),
                        lots = lines,
                        totalCount = todayMeasurements.size,
                        compliantPercent = todayMeasurements.takeIf { it.isNotEmpty() }
                            ?.let { list -> list.count { it.status == "verde" } * 100.0 / list.size },
                        pendingAlerts = state.pendingAlerts.size,
                        simulatedCount = todayMeasurements.count { it.isSimulated },
                        generatedBy = "${session.name} (${session.role.label})"
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Resumen del día - $FARM")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Compartir resumen del día"))
                },
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Compartir resumen")
            }
        }

        if (state.pendingReviews.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("${state.pendingReviews.size} revisión(es) pedida(s)", status.warning)
                TextButton(onClick = onOpenAlerts) { Text("Ver en Alertas") }
            }
        }

        if (todayLots.isEmpty()) {
            Text(
                "Hoy todavía no hay lotes. Aparecerán cuando alguien registre la primera medición del día.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        todayLots.forEach { lot ->
            val analysis = analyses[lot.id]
            val level = analysis?.analysis?.verdict()?.level
            val count = todayMeasurements.count { it.lotId == lot.id }
            val pending = pendingByLot[lot.id] ?: 0
            val simulated = todayMeasurements.any { it.lotId == lot.id && it.isSimulated }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            lot.tank,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (lot.closed) StatusPill("CERRADO", status.info)
                        if (level != null) StatusPill(level.label, verdictTone(level, status))
                    }
                    Text(
                        text = when {
                            count == 0 -> "Sin mediciones hoy."
                            else -> "$count medición(es) hoy" +
                                (analysis?.let { " · última a las ${timeFormat.format(Date(it.latestTimestamp))}" } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (analysis?.overdue == true && !lot.closed) {
                        Text(
                            "Medición atrasada: tocaba volver a medir a las ${timeFormat.format(Date(analysis.dueAtMillis))}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = status.warning.content
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pending > 0) {
                            StatusPill("$pending alerta(s) pendiente(s)", status.alert)
                        } else if (count > 0) {
                            StatusPill("Sin alertas pendientes", status.good)
                        }
                        SalePill(lot.sale)
                        if (simulated) SimulatedBadge()
                    }
                    if (pending > 0) {
                        TextButton(onClick = onOpenAlerts) { Text("Ver alertas") }
                    }
                }
            }
        }

        val milkers = SupervisionStats.groupBy(
            todayMeasurements.map { it.toPoint(state.lotName(it.lotId)) }
        ) { it.milkerName }
        Text(
            "Actividad de los lecheros hoy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (milkers.isEmpty()) {
            Text(
                "Nadie ha medido hoy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            GroupTable(milkers, timeFormat, showAverages = false)
        }

        Text(
            "Comparativa de los últimos 7 días",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompareBy.entries.forEach { option ->
                FilterChip(
                    selected = compareBy == option,
                    onClick = { compareBy = option },
                    label = { Text(option.label) }
                )
            }
        }
        val points = valid.map { it.toPoint(state.lotName(it.lotId)) }
        val groups = when (compareBy) {
            CompareBy.TANK -> SupervisionStats.groupBy(points) { it.lotId }
            CompareBy.MILKER -> SupervisionStats.groupBy(points) { it.milkerName }
        }
        if (groups.isEmpty()) {
            Text(
                "No hay mediciones en los últimos 7 días para comparar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            GroupTable(groups, timeFormat, showAverages = true)
            if (groups.any { it.simulatedCount > 0 }) {
                Text(
                    "Incluye mediciones SIMULADAS (modo DEMO): no son datos reales de un sensor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = status.simulated.content
                )
            }
        }
    }
}

private fun verdictTone(level: VerdictLevel, status: com.example.myapplication.ui.theme.StatusColors): StatusTone = when (level) {
    VerdictLevel.GOOD -> status.good
    VerdictLevel.REVIEW -> status.warning
    VerdictLevel.STOP -> status.alert
}

/** Filas comparables: nombre, cantidad, % conforme y, si se pide, promedios. */
@Composable
private fun GroupTable(groups: List<GroupStats>, timeFormat: SimpleDateFormat, showAverages: Boolean) {
    val status = AppTheme.status
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEachIndexed { index, g ->
                if (index > 0) HorizontalDivider()
                val tone = when {
                    g.compliantPercent >= 90 -> status.good
                    g.compliantPercent >= 60 -> status.warning
                    else -> status.alert
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${g.count} medición(es) · última ${timeFormat.format(Date(g.lastMillis))}" +
                                if (g.simulatedCount > 0) " · ${g.simulatedCount} simulada(s)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill("%.0f %% conformes".format(g.compliantPercent), tone)
                }
                if (showAverages) {
                    Text(
                        "Prom.: %.1f °C · pH %.2f · %.2f mS/cm · turbidez %.0f %%".format(
                            g.avgTemperature, g.avgPh, g.avgConductivity, g.avgTurbidity
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
