package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
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
import com.example.myapplication.model.analysis.ForecastEngine
import com.example.myapplication.model.analysis.GlossaryEntry
import com.example.myapplication.model.analysis.ProgressReport
import com.example.myapplication.model.analysis.ProgressTrend
import com.example.myapplication.model.analysis.Verdict
import com.example.myapplication.model.analysis.VerdictLevel
import com.example.myapplication.model.analysis.LotAnalysis
import com.example.myapplication.model.analysis.MilkAnalysis
import com.example.myapplication.model.analysis.PriorityLevel
import com.example.myapplication.model.analysis.ParameterFinding
import com.example.myapplication.model.analysis.statusText
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
fun AnalysisSection(
    analysis: MilkAnalysis,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    /** Mostrar los recuadros con el valor de cada parametro (la pantalla Medir ya los muestra aparte). */
    showFindings: Boolean = true,
    /** Las tarjetas de detalle (que se detecto y por que) quedan tras un boton "Ver detalle"; las acciones siguen visibles. */
    detailsCollapsible: Boolean = false
) {
    var showDetails by remember { mutableStateOf(!detailsCollapsible) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTitle) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Análisis y recomendaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        PriorityCard(analysis)

        if (showFindings) FindingsGrid(analysis.findings)

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
        if (detailsCollapsible && cards.isNotEmpty()) {
            OutlinedButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
                Text(if (showDetails) "  Ocultar detalle del análisis" else "  Ver detalle del análisis (${cards.size})")
            }
        }
        if (showDetails) {
            cards.forEach { card ->
                key(card.kind, card.title) { InsightCard(card) }
            }
        }

        Text(
            text = if (analysis.simulated) {
                "Datos simulados (modo DEMO). Las causas son posibilidades, no diagnósticos confirmados."
            } else {
                "Las causas son posibilidades, no diagnósticos confirmados."
            },
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

@OptIn(ExperimentalLayoutApi::class)
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
            // Nombre del lote con todo el ancho; las etiquetas van debajo para que nada se apriete.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${item.readings} medición(es) · última ${dateFormat.format(Date(item.latestTimestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Ocultar análisis" else "Ver análisis"
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill(text = item.analysis.priority.label.uppercase(), tone = tone, icon = icon)
                if (item.overdue) StatusPill(text = "Medición atrasada", tone = AppTheme.status.warning)
            }
            Text(text = item.analysis.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (item.overdue) {
                Text(
                    text = "Convenía volver a medir a las ${dateFormat.format(Date(item.dueAtMillis))}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                AnalysisSection(item.analysis, showTitle = false, detailsCollapsible = true)
            }
        }
    }
}


// ---------------------------------------------------------------------------------------------
// Resultado de una medicion: veredicto, pasos, proyeccion, seguimiento y glosario
// ---------------------------------------------------------------------------------------------

@Composable
private fun verdictStyle(level: VerdictLevel): Pair<StatusTone, ImageVector> {
    val status = AppTheme.status
    return when (level) {
        VerdictLevel.GOOD -> status.good to Icons.Default.CheckCircle
        VerdictLevel.REVIEW -> status.warning to Icons.Default.Warning
        VerdictLevel.STOP -> status.alert to Icons.Default.Error
    }
}

/** Estado de la muestra de un vistazo: buena calidad, revisar o no usar hasta verificar. */
@Composable
fun VerdictCard(verdict: Verdict, simulated: Boolean, modifier: Modifier = Modifier) {
    val (tone, icon) = verdictStyle(verdict.level)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container),
        border = BorderStroke(2.dp, tone.content.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tone.content.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(32.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resultado de la medición",
                        style = MaterialTheme.typography.labelMedium,
                        color = tone.content.copy(alpha = 0.85f)
                    )
                    Text(
                        text = verdict.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tone.content
                    )
                }
            }
            Text(text = verdict.message, style = MaterialTheme.typography.bodyMedium, color = tone.content)
        }
    }
}

/** "Que hacer ahora": pasos con casillas que el trabajador va marcando. */
@Composable
fun NextStepsCard(steps: List<String>, level: VerdictLevel, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val checked = remember(steps) { mutableStateListOf<Boolean>().apply { repeat(steps.size) { add(false) } } }
    val done = checked.count { it }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Qué hacer ahora",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$done de ${steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checked[index] = !checked[index] },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                    Text(text = step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            if (level != VerdictLevel.GOOD && done == steps.size) {
                Text(
                    text = "Cuando termine, pulse \"Medir de nuevo\" para comprobar si mejoró.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Seguimiento: como quedo esta medicion respecto de la muestra anterior del lote (por ejemplo despues de actuar). */
@Composable
fun ProgressCard(report: ProgressReport, modifier: Modifier = Modifier) {
    val status = AppTheme.status
    val tone = when (report.overall) {
        ProgressTrend.IMPROVED -> status.good
        ProgressTrend.WORSENED -> status.alert
        ProgressTrend.SAME -> status.info
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = report.headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tone.content
            )
            report.items.filter { it.trend != ProgressTrend.SAME }.forEach { item ->
                val word = if (item.trend == ProgressTrend.IMPROVED) "mejoró" else "empeoró"
                Text(
                    text = "${item.parameter.label}: ${item.previousText} → ${item.currentText} ($word)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tone.content
                )
            }
            Text(
                text = "Comparado con la muestra tomada hace ${ForecastEngine.formatMinutes(report.minutesBetween.toInt())} en el mismo lote.",
                style = MaterialTheme.typography.bodySmall,
                color = tone.content.copy(alpha = 0.85f)
            )
        }
    }
}

/** Ayuda desplegable: que mide cada parametro, en palabras simples. */
@Composable
fun GlossaryCard(entries: List<GlossaryEntry>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¿Qué mide cada parámetro?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Ocultar ayuda" else "Ver ayuda"
                )
            }
            if (expanded) {
                entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = entry.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(text = entry.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}


/** Color del recuadro segun la gravedad de la desviacion. */
fun levelSensorStatus(level: DeviationLevel): SensorStatus = when (level) {
    DeviationLevel.IN_RANGE -> SensorStatus.GOOD
    DeviationLevel.SLIGHT, DeviationLevel.MODERATE, DeviationLevel.UNRELIABLE -> SensorStatus.WARNING
    DeviationLevel.CONSIDERABLE, DeviationLevel.CRITICAL -> SensorStatus.ALERT
}

/** Los cuatro parametros en recuadros de 2 x 2: nombre, valor y estado. */
@Composable
fun FindingsGrid(findings: List<ParameterFinding>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        findings.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { f -> FindingBox(f, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FindingBox(finding: ParameterFinding, modifier: Modifier = Modifier) {
    val status = AppTheme.status
    val tone = when (levelSensorStatus(finding.level)) {
        SensorStatus.GOOD -> status.good
        SensorStatus.WARNING -> status.warning
        SensorStatus.ALERT -> status.alert
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = finding.parameter.label,
                style = MaterialTheme.typography.labelMedium,
                color = tone.content.copy(alpha = 0.85f)
            )
            Text(
                text = finding.valueText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tone.content
            )
            Text(
                text = finding.statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = tone.content
            )
            Text(
                text = "Rango: ${finding.reference}",
                style = MaterialTheme.typography.bodySmall,
                color = tone.content.copy(alpha = 0.85f)
            )
        }
    }
}
