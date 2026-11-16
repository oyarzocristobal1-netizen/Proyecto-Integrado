package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.analysis.AnalysisCard
import com.example.myapplication.model.analysis.CardKind
import com.example.myapplication.model.analysis.DeviationLevel
import com.example.myapplication.model.analysis.LotAnalysis
import com.example.myapplication.model.analysis.MilkAnalysis
import com.example.myapplication.model.analysis.PriorityLevel
import com.example.myapplication.model.analysis.valueText
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Color e icono de cada nivel de prioridad (NORMAL, PREVENTIVO, ADVERTENCIA, CRITICO). */
@Composable
private fun priorityStyle(priority: PriorityLevel): Pair<StatusTone, ImageVector> {
    val status = AppTheme.status
    return when (priority) {
        PriorityLevel.NORMAL -> status.good to Icons.Default.CheckCircle
        PriorityLevel.PREVENTIVE -> status.info to Icons.Default.Info
        PriorityLevel.WARNING -> status.warning to Icons.Default.Warning
        PriorityLevel.CRITICAL -> status.alert to Icons.Default.Error
    }
}

@Composable
private fun levelTone(level: DeviationLevel?): StatusTone {
    val status = AppTheme.status
    return when (level) {
        DeviationLevel.IN_RANGE -> status.good
        DeviationLevel.CRITICAL -> status.alert
        DeviationLevel.MODERATE, DeviationLevel.CONSIDERABLE, DeviationLevel.UNRELIABLE -> status.warning
        else -> status.info
    }
}

/**
 * "Analisis y recomendaciones": estado general con su prioridad, parametros, acciones y tarjetas
 * desplegables (que esta pasando, posibles causas, que revisar y que hacer).
 */
@Composable
fun AnalysisSection(analysis: MilkAnalysis, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTitle) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Análisis y recomendaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (analysis.simulated) SimulatedBadge()
            }
        }

        PriorityCard(analysis)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            analysis.findings.forEach { f ->
                val text = if (f.level == DeviationLevel.UNRELIABLE) {
                    "${f.parameter.label} ${f.valueText} · lectura dudosa"
                } else {
                    "${f.parameter.label} ${f.valueText} · ${f.level.label.lowercase()}"
                }
                StatusPill(text = text, tone = levelTone(f.level))
            }
        }

        if (analysis.actions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Acciones recomendadas",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    analysis.actions.forEachIndexed { index, action ->
                        Text(text = "${index + 1}. $action", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val cards = analysis.reliabilityCards + analysis.parameterCards + analysis.combinedCards + analysis.historyCards
        cards.forEach { card ->
            key(card.kind, card.title) { InsightCard(card) }
        }

        analysis.historyNote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = analysis.disclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PriorityCard(analysis: MilkAnalysis) {
    val (tone, icon) = priorityStyle(analysis.priority)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container),
        border = BorderStroke(1.5.dp, tone.content.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tone.content.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prioridad: ${analysis.priority.label.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tone.content
                    )
                    Text(
                        text = analysis.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tone.content
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = analysis.summary, style = MaterialTheme.typography.bodyMedium, color = tone.content)
            Text(
                text = analysis.priority.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = tone.content.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = tone.content.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Seguimiento: ${analysis.followUp}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = tone.content
            )
        }
    }
}

private fun kindLabel(kind: CardKind): String = when (kind) {
    CardKind.PARAMETER -> "Parámetro"
    CardKind.COMBINATION -> "Combinado"
    CardKind.HISTORY -> "Historial"
    CardKind.RELIABILITY -> "Lectura"
}

/** Tarjeta desplegable con las cuatro preguntas: que sucede, posibles causas, que revisar y que hacer. */
@Composable
private fun InsightCard(card: AnalysisCard) {
    var expanded by remember { mutableStateOf(false) }
    val tone = levelTone(card.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tone.content.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = kindLabel(card.kind), tone = tone)
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Ocultar detalle" else "Ver detalle"
                )
            }
            if (expanded) {
                InsightText("¿Qué está sucediendo?", card.whatHappens)
                InsightList("Posibles causas", card.possibleCauses)
                InsightList("¿Qué revisar?", card.whatToCheck)
                InsightText("Recomendación", card.recommendation, emphasized = true)
            }
        }
    }
}

@Composable
private fun InsightText(title: String, text: String, emphasized: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun InsightList(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        items.forEach { Text(text = "• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

/**
 * Analisis historico para el encargado: estado de cada lote segun sus mediciones registradas
 * (repeticion, tendencias y cambios), con el detalle desplegable.
 */
@Composable
fun HistoricalAnalysisSection(items: List<LotAnalysis>, lotName: (String) -> String, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Análisis histórico",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Recomendaciones según las mediciones registradas de cada lote, ordenadas por prioridad.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { item ->
            key(item.lotId) { LotAnalysisRow(item, lotName(item.lotId)) }
        }
    }
}

@Composable
private fun LotAnalysisRow(item: LotAnalysis, name: String) {
    var expanded by remember { mutableStateOf(false) }
    val (tone, icon) = priorityStyle(item.analysis.priority)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tone.content.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = item.analysis.priority.label.uppercase(), tone = tone, icon = icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${item.readings} medición(es) · última ${dateFormat.format(Date(item.latestTimestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.analysis.simulated) SimulatedBadge()
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Ocultar análisis" else "Ver análisis"
                )
            }
            Text(text = item.analysis.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (expanded) {
                AnalysisSection(item.analysis, showTitle = false)
            }
        }
    }
}
