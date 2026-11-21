package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La app esta mostrando o guardando datos simulados (modo DEMO). La pone MainScaffold; mientras sea true,
 * cada titulo de pantalla muestra una etiqueta "DEMO" pequena en lugar de marcar cada dato por separado.
 */
val LocalDemoData = compositionLocalOf { false }

/** Etiqueta pequena "DEMO": se ve, pero no distrae. */
@Composable
private fun DemoTag() {
    Surface(
        shape = RoundedCornerShape(50),
        color = com.example.myapplication.ui.theme.AppTheme.status.simulated.container
    ) {
        Text(
            "DEMO",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = com.example.myapplication.ui.theme.AppTheme.status.simulated.content,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/** Titulo de pantalla con subtitulo (por ejemplo "Rangos de calidad" / "Fundo San Carlos"). */
@Composable
fun ScreenTitle(title: String, subtitle: String = "Fundo San Carlos") {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (LocalDemoData.current) DemoTag()
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Aviso informativo con icono, al inicio de un formulario. */
@Composable
fun InfoBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** Seccion del formulario en una tarjeta: icono + titulo, el contenido y una linea de referencia. */
@Composable
fun SectionCard(
    emoji: String,
    title: String,
    reference: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji, fontSize = 18.sp)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
            if (reference != null) {
                Text(
                    reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Boton principal redondeado (pildora) con icono a la izquierda y otro opcional a la derecha. */
@Composable
fun PrimaryPillButton(
    text: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Boton secundario redondeado con borde e icono a la derecha. */
@Composable
fun SecondaryPillButton(text: String, trailingIcon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(10.dp))
        Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** Nota de advertencia corta al pie de un formulario. */
@Composable
fun WarningNote(text: String) {
    Text(
        "⚠️ $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
