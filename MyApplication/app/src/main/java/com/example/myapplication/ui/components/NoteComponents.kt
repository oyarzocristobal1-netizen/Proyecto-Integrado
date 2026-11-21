package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ReviewRules

/**
 * Nota opcional y peticion de revision, que se guardan junto con la medicion.
 * Si el analisis recomienda revisar o no usar la leche, se sugiere pedir revision.
 */
@Composable
fun MeasurementNoteCard(
    note: String,
    onNoteChange: (String) -> Unit,
    requestReview: Boolean,
    onRequestReviewChange: (Boolean) -> Unit,
    suggestReview: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nota para el encargado (opcional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= ReviewRules.NOTE_MAX) onNoteChange(it) },
                placeholder = { Text("Ej.: vaca con mastitis, se limpió el estanque") },
                enabled = enabled,
                supportingText = { Text("${note.length}/${ReviewRules.NOTE_MAX}") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Checkbox(checked = requestReview, onCheckedChange = onRequestReviewChange, enabled = enabled)
                Column {
                    Text("Pedir revisión del encargado", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (suggestReview) "Recomendado: el análisis pide revisar esta muestra."
                        else "El encargado verá un aviso para revisar esta medición.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
