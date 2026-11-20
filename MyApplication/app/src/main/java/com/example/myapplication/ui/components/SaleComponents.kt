package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Lot
import com.example.myapplication.model.SaleDecision
import com.example.myapplication.model.SaleRelease
import com.example.myapplication.model.analysis.LotAnalysis
import com.example.myapplication.model.analysis.verdict
import com.example.myapplication.ui.theme.AppTheme
import java.text.SimpleDateFormat

/** Etiqueta con la decision de venta del lote (nada si aun no se decidio). */
@Composable
fun SalePill(decision: SaleDecision?, modifier: Modifier = Modifier) {
    when (decision) {
        SaleDecision.APTO -> StatusPill("APTO PARA VENTA", AppTheme.status.good, modifier)
        SaleDecision.NO_APTO -> StatusPill("NO APTO PARA VENTA", AppTheme.status.alert, modifier)
        null -> Unit
    }
}

/** Quien decidio, cuando y con que nota (para mostrar bajo la tarjeta del lote). */
fun saleDetailText(lot: Lot, dateFormat: SimpleDateFormat): String? {
    val decision = lot.sale ?: return null
    return buildString {
        append(decision.label)
        if (lot.saleByName.isNotBlank()) append(" · por ${lot.saleByName}")
        lot.saleAt?.let { append(" · ${dateFormat.format(it)}") }
        if (lot.saleNote.isNotBlank()) append("\nNota: ${lot.saleNote}")
    }
}

/**
 * Dialogo para liberar un lote para venta. Muestra lo que dice el analisis como sugerencia;
 * la decision es del encargado y, si va contra el analisis, debe explicarla.
 */
@Composable
fun SaleDialog(
    lot: Lot,
    analysis: LotAnalysis?,
    measurementCount: Int,
    simulatedData: Boolean,
    saving: Boolean,
    onConfirm: (decision: SaleDecision?, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val verdict = analysis?.analysis?.verdict()
    val level = verdict?.level
    val overdue = analysis?.overdue == true
    val suggestion = SaleRelease.suggestion(level)
    var decision by rememberSaveable(lot.id) { mutableStateOf(lot.sale ?: suggestion) }
    var note by rememberSaveable(lot.id) { mutableStateOf(lot.saleNote) }
    val required = decision?.let { SaleRelease.noteRequiredReason(it, level, overdue) }
    val error = SaleRelease.validate(decision, level, note, measurementCount, overdue)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Liberar lote ${lot.tank}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        measurementCount <= 0 -> "Este lote no tiene mediciones válidas todavía."
                        verdict != null -> "Análisis: ${verdict.level.label} · $measurementCount medición(es)" +
                            if (overdue) " · medición atrasada" else ""
                        else -> "$measurementCount medición(es)"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when (suggestion) {
                        SaleDecision.APTO -> "El análisis sugiere: apto para venta."
                        SaleDecision.NO_APTO -> "El análisis sugiere: no apto para venta."
                        null -> "El análisis no sugiere una decisión: evalúa con tu criterio."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (simulatedData) {
                    Text(
                        "Datos SIMULADOS (modo DEMO): esta decisión es solo de demostración, no corresponde a leche real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.status.simulated.content
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaleDecision.entries.forEach { option ->
                        FilterChip(
                            selected = decision == option,
                            onClick = { if (!saving) decision = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= SaleRelease.NOTE_MAX) note = it },
                    label = { Text(if (required != null) "Nota (obligatoria)" else "Nota (opcional)") },
                    enabled = !saving,
                    isError = required != null && note.trim().length < SaleRelease.NOTE_MIN_WHEN_REQUIRED && note.isNotEmpty(),
                    supportingText = { Text(required ?: "${note.length}/${SaleRelease.NOTE_MAX}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Queda guardado quién lo decidió y cuándo. Puedes cambiarlo después si la situación cambia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(decision, note) },
                enabled = !saving && error == null
            ) { Text(if (saving) "Guardando…" else "Guardar decisión") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}
