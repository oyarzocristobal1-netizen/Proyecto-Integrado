package com.example.myapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.AppTheme
import java.util.Locale

private val AXIS_WIDTH = 36.dp

/**
 * Grafico de linea simple (sin librerias externas) con el promedio diario de los ultimos dias
 * y una franja con el rango aceptable. Los dias sin mediciones quedan en blanco: no se inventan.
 */
@Composable
fun TrendChart(
    title: String,
    unit: String,
    labels: List<String>,
    values: List<Double?>,
    bandMin: Double?,
    bandMax: Double?,
    decimals: Int,
    modifier: Modifier = Modifier
) {
    val present = values.filterNotNull()
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = AppTheme.status.good.container
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fmt = "%.${decimals}f"

    val description = if (present.isEmpty()) {
        "$title: sin mediciones en los últimos ${labels.size} días."
    } else {
        "$title, promedio diario: " + labels.zip(values).joinToString("; ") { (l, v) ->
            "$l " + (v?.let { String.format(Locale.US, fmt, it) + " $unit" } ?: "sin datos")
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (bandMin != null || bandMax != null) {
                    Text(
                        "Rango: " + listOfNotNull(bandMin, bandMax).joinToString(" – ") { String.format(Locale.US, fmt, it) } + " $unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (present.isEmpty()) {
                Text(
                    "Sin mediciones en los últimos ${labels.size} días.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                return@Column
            }

            // Escala vertical: los valores y la franja, con un margen.
            val candidates = present + listOfNotNull(bandMin, bandMax)
            var low = candidates.min()
            var high = candidates.max()
            if (high - low < 1e-6) {
                low -= 1.0
                high += 1.0
            }
            val margin = (high - low) * 0.12
            low -= margin
            high += margin

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(
                    modifier = Modifier
                        .width(AXIS_WIDTH)
                        .height(120.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(String.format(Locale.US, fmt, high), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format(Locale.US, fmt, low), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                ) {
                    val n = values.size
                    if (n == 0) return@Canvas
                    // Cada dia ocupa una columna del mismo ancho que su etiqueta (punto al centro).
                    val cell = size.width / n
                    fun x(i: Int) = cell * (i + 0.5f)
                    fun y(v: Double) = (size.height * (1 - (v - low) / (high - low))).toFloat()

                    // Franja del rango aceptable
                    val top = bandMax?.let { y(it) } ?: 0f
                    val bottom = bandMin?.let { y(it) } ?: size.height
                    if (bandMin != null || bandMax != null) {
                        drawRect(color = bandColor, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
                    }
                    // Lineas guia
                    for (g in 0..2) {
                        val gy = size.height * g / 2f
                        drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                    }
                    // Linea por tramos: se corta en los dias sin datos
                    var path: Path? = null
                    values.forEachIndexed { i, v ->
                        if (v == null) {
                            path?.let { drawPath(it, lineColor, style = Stroke(width = 3.dp.toPx())) }
                            path = null
                        } else {
                            val p = path
                            if (p == null) {
                                path = Path().apply { moveTo(x(i), y(v)) }
                            } else {
                                p.lineTo(x(i), y(v))
                            }
                        }
                    }
                    path?.let { drawPath(it, lineColor, style = Stroke(width = 3.dp.toPx())) }
                    values.forEachIndexed { i, v ->
                        if (v != null) {
                            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x(i), y(v)))
                            drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(x(i), y(v)))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = AXIS_WIDTH + 6.dp)) {
                labels.forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
