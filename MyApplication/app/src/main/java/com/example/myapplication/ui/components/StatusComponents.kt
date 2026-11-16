package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.StatusTone

/** Etiqueta redondeada de estado (semaforo, origen, etc.). */
@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(shape = RoundedCornerShape(50), color = tone.container, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                color = tone.content,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Marca de dato SIMULADO (modo DEMO): nunca se presenta como medicion real. */
@Composable
fun SimulatedBadge(modifier: Modifier = Modifier) {
    StatusPill(
        text = "SIMULADA",
        tone = AppTheme.status.simulated,
        icon = Icons.Outlined.Science,
        modifier = modifier.semantics { contentDescription = "Dato simulado, no es una medición real" }
    )
}

/** Medicion guardada en el telefono que aun no confirma el servidor. */
@Composable
fun PendingSyncBadge(modifier: Modifier = Modifier) {
    StatusPill(
        text = "Sin sincronizar",
        tone = AppTheme.status.warning,
        icon = Icons.Outlined.CloudUpload,
        modifier = modifier
    )
}

/** Aviso con icono a lo ancho de la pantalla. */
@Composable
fun NoticeBanner(text: String, tone: StatusTone, icon: ImageVector, modifier: Modifier = Modifier, title: String? = null) {
    Surface(shape = RoundedCornerShape(14.dp), color = tone.container, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = tone.content)
            Column {
                if (title != null) {
                    Text(title, color = tone.content, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text(text, color = tone.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Sin internet: lo registrado queda en el telefono y se envia despues. */
@Composable
fun OfflineBanner(pendingCount: Int, modifier: Modifier = Modifier) {
    val pending = if (pendingCount > 0) " Hay $pendingCount medición(es) por enviar." else ""
    NoticeBanner(
        title = "Sin conexión",
        text = "Puedes seguir registrando: se guarda en el teléfono y se enviará al volver la red.$pending",
        tone = AppTheme.status.warning,
        icon = Icons.Outlined.CloudOff,
        modifier = modifier
    )
}

/** Aviso de modo DEMO en la pantalla Medir. */
@Composable
fun DemoModeBanner(modifier: Modifier = Modifier) {
    NoticeBanner(
        title = "MODO DEMO · Datos simulados",
        text = "Las lecturas las genera la app para probarla sin la caja de sensores. " +
            "No corresponden a una muestra real y se guardan marcadas como SIMULADAS.",
        tone = AppTheme.status.simulated,
        icon = Icons.Outlined.Science,
        modifier = modifier
    )
}

/** Pantalla o lista vacia: explica por que no hay datos y, si corresponde, que hacer. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Confirmacion antes de una accion que no se puede deshacer. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
