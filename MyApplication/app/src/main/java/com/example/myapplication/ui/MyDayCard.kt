package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.model.DayTime
import com.example.myapplication.model.MyDay
import com.example.myapplication.model.SupervisionPoint
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.components.SimulatedBadge
import com.example.myapplication.ui.components.StatusPill
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.RecordsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun SavedMeasurement.toOwnPoint() = SupervisionPoint(
    milkerName = milkerName, lotId = lotId, epochMillis = dateTime.time, status = status, simulated = isSimulated,
    temperature = temperature, ph = ph, conductivity = conductivity, turbidity = turbidity
)

/**
 * "Mi dia" (trabajador): cuantas mediciones hizo hoy, cuantas salieron conformes, que lote le toca medir
 * y como van las revisiones que pidio. Solo usa sus propias mediciones; lo simulado se marca.
 */
@Composable
fun MyDayCard(state: RecordsUiState, session: UserSession, now: Long, modifier: Modifier = Modifier) {
    val status = AppTheme.status
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val own = state.recentValid.filter { it.milkerUid == session.uid }
    val dayStart = MyDay.startOfDay(now)
    val summary = MyDay.summarize(own.map { it.toOwnPoint() }, dayStart)
    val plan = ownReminderPlan(state, session.uid, now)
    val reviews = own.filter { it.reviewRequested && it.dateTime.time >= dayStart }
    val reviewsPending = reviews.count { !it.reviewDone }
    val reviewsDone = reviews.count { it.reviewDone }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mi día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (summary.count == 0) {
                Text(
                    "Todavía no mediste hoy. Ve a Medir para registrar la primera muestra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val percent = summary.compliantPercent ?: 0.0
                    StatusPill(
                        "%.0f %% conformes".format(percent),
                        when {
                            percent >= 90 -> status.good
                            percent >= 60 -> status.warning
                            else -> status.alert
                        }
                    )
                }
                Text(
                    "${summary.count} medición(es) hoy · ${summary.compliantCount} conforme(s)" +
                        (summary.lastMillis?.let { " · última a las ${timeFormat.format(Date(it))}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            plan.overdue.forEach { item ->
                Text(
                    "Lote ${item.tank}: te tocaba medir ${DayTime.label(item.dueAtMillis, now)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = status.warning.content
                )
            }
            plan.upcoming.forEach { item ->
                Text(
                    "Lote ${item.tank}: próxima medición ${DayTime.label(item.dueAtMillis, now)}.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (summary.count > 0 && plan.overdue.isEmpty() && plan.upcoming.isEmpty()) {
                Text("No tienes mediciones pendientes.", style = MaterialTheme.typography.bodyMedium)
            }
            if (reviews.isNotEmpty()) {
                Text(
                    "Revisiones que pediste: $reviewsPending pendiente(s) · $reviewsDone hecha(s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
