package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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

/**
 * Dialogo con un campo de texto (motivo, nota, nombre). Muestra el contador de caracteres y el
 * error de validacion mientras se escribe; "Guardar" solo se habilita si el texto es valido.
 *
 * @param validate devuelve un mensaje si el texto no es valido, o null si esta bien.
 * @param maxLength tope de caracteres (el mismo que exigen las reglas de Firestore).
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    confirmLabel: String,
    saving: Boolean,
    validate: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    /** Cambia con cada elemento distinto, para que el texto no se arrastre de un dialogo a otro. */
    stateKey: Any = title
) {
    var text by rememberSaveable(stateKey) { mutableStateOf(initial) }
    val error = validate(text)
    // El error solo se muestra cuando ya se escribio algo (o el campo es obligatorio y se toco).
    val showError = error != null && text.isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLength) text = it },
                    label = { Text(label) },
                    placeholder = if (placeholder != null) {
                        { Text(placeholder) }
                    } else {
                        null
                    },
                    singleLine = singleLine,
                    enabled = !saving,
                    isError = showError,
                    supportingText = { Text(if (showError) error.orEmpty() else "${text.length}/$maxLength") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = !saving && error == null) {
                Text(if (saving) "Guardando…" else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}
