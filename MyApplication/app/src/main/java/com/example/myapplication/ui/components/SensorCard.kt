package com.example.myapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.StatusTone

enum class SensorStatus {
    GOOD,
    WARNING,
    ALERT
}

/** Escala de frio a calor para la barra de temperatura (azul -> verde -> amarillo -> rojo). */
val TemperatureScale: Brush = Brush.horizontalGradient(
    listOf(Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFFE53935))
)

/** Colores del estado (se adaptan al modo claro u oscuro). */
@Composable
fun sensorStatusTone(status: SensorStatus): StatusTone = when (status) {
    SensorStatus.GOOD -> AppTheme.status.good
    SensorStatus.WARNING -> AppTheme.status.warning
    SensorStatus.ALERT -> AppTheme.status.alert
}

@Composable
fun SensorCard(
    title: String,
    valueString: String,
    unit: String,
    statusText: String,
    status: SensorStatus,
    icon: ImageVector,
    referenceRange: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    /** Si se indica, la barra usa esta escala de color (por ejemplo, frio -> calor). */
    progressBrush: Brush? = null
) {
    val tone = sensorStatusTone(status)
    val statusColor by animateColorAsState(targetValue = tone.content, label = "statusColor")
    val statusContainerColor by animateColorAsState(targetValue = tone.container, label = "statusContainer")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: $valueString $unit, $statusText. Rango de referencia $referenceRange."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Icono + titulo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Etiqueta de estado en su propia linea (nunca se corta)
            Surface(shape = RoundedCornerShape(50), color = statusContainerColor) {
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Valor grande + unidad
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = valueString,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LevelBar(
                progress = (progress ?: 0f).coerceIn(0f, 1f),
                color = statusColor,
                brush = progressBrush,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Rango ref: $referenceRange",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Barra de nivel con un punto que marca la lectura actual. */
@Composable
private fun LevelBar(progress: Float, color: Color, brush: Brush?, trackColor: Color) {
    val animated by animateFloatAsState(targetValue = progress, label = "level")
    val thumbRing = MaterialTheme.colorScheme.surfaceContainerLowest
    val thumbCenter = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val barHeight = 6.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        val thumbRadius = 7.dp.toPx()
        val usable = size.width - 2 * thumbRadius
        val x = thumbRadius + usable * animated

        if (brush != null) {
            // Escala completa (frio -> calor); el punto indica donde esta la lectura
            drawRoundRect(brush = brush, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        } else {
            drawRoundRect(color = trackColor, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
            drawRoundRect(color = color, topLeft = Offset(0f, top), size = Size(x, barHeight), cornerRadius = radius)
        }
        drawCircle(color = thumbRing, radius = thumbRadius, center = Offset(x, size.height / 2f))
        drawCircle(
            color = if (brush != null) thumbCenter else color,
            radius = thumbRadius - 2.dp.toPx(),
            center = Offset(x, size.height / 2f)
        )
    }
}
