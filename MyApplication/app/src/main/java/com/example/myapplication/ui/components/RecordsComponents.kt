package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Lot
import com.example.myapplication.data.SavedMeasurement
import java.text.SimpleDateFormat
import java.util.Locale

private val dayTime = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
private val dayOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

/** Lote activo donde se registran las mediciones, con el boton para registrar la lectura actual. */
@Composable
fun CurrentLotCard(
    currentLot: Lot?,
    saving: Boolean,
    onChangeLot: () -> Unit,
    onRecord: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lote activo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = currentLot?.let { "${it.tank} · ${dayOnly.format(it.date)}" } ?: "Sin lote elegido",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Button(
                    onClick = onChangeLot,
                    enabled = !saving,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(if (currentLot == null) "Elegir" else "Cambiar", fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onRecord,
                enabled = currentLot != null && !saving,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Registrar medición", fontWeight = FontWeight.SemiBold)
            }
            if (currentLot == null) {
                Text(
                    text = "Elige o crea el lote de hoy para poder registrar mediciones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Elegir uno de los lotes de hoy o crear el de un estanque. */
@Composable
fun LotPickerDialog(
    todayLots: List<Lot>,
    currentLotId: String?,
    saving: Boolean,
    onSelect: (String) -> Unit,
    onCreate: (tank: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tank by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Lote de hoy") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (todayLots.isEmpty()) {
                    Text("Todavía no hay lotes hoy.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Lotes de hoy", style = MaterialTheme.typography.labelLarge)
                    todayLots.forEach { lot ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = lot.id == currentLotId,
                                    enabled = !saving,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(lot.id) }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = lot.id == currentLotId, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(lot.tank, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Creado por ${lot.createdByName.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Nuevo lote", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = tank,
                    onValueChange = { tank = it },
                    label = { Text("Estanque") },
                    placeholder = { Text("Ej.: Estanque 1") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Se crea un lote por estanque y día. Si ya existe, se usa el mismo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(tank) }, enabled = !saving && tank.isNotBlank()) {
                Text(if (saving) "Creando…" else "Crear y usar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cerrar") }
        }
    )
}

/** Titulo del historial con filtro por lote (solo para quien ve todo el historial). */
@Composable
fun HistoryHeader(
    title: String,
    count: Int,
    canFilter: Boolean,
    lots: List<Lot>,
    filterLotId: String?,
    onFilter: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (canFilter) {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        lots.firstOrNull { it.id == filterLotId }?.tank ?: "Ver todos los lotes",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Filtrar por lote")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Todos los lotes") },
                        onClick = {
                            expanded = false
                            onFilter(null)
                        }
                    )
                    lots.forEach { lot ->
                        DropdownMenuItem(
                            text = { Text("${lot.tank} · ${dayOnly.format(lot.date)}") },
                            onClick = {
                                expanded = false
                                onFilter(lot.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Valor de un sensor con su icono de color (historial). */
@Composable
private fun SensorValueChip(icon: ImageVector, iconColor: Color, iconBackground: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Una medicion guardada en el historial. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedMeasurementRow(
    measurement: SavedMeasurement,
    lotName: String,
    showMilker: Boolean
) {
    val (container, content, label) = when (measurement.status) {
        "verde" -> Triple(Color(0xFFE3F1E4), Color(0xFF1B5E20), "NORMAL")
        "ambar" -> Triple(Color(0xFFFFF1D0), Color(0xFF6B4500), "PRECAUCIÓN")
        "rojo" -> Triple(Color(0xFFFDE4E4), Color(0xFFB3261E), "FUERA DE RANGO")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "—")
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${dayTime.format(measurement.dateTime)} · $lotName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = container) {
                    Text(
                        text = label,
                        color = content,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SensorValueChip(
                    Icons.Default.Thermostat, Color(0xFFC62828), Color(0xFFFDE4E4),
                    "%.1f°C".format(measurement.temperature)
                )
                SensorValueChip(
                    Icons.Default.Science, Color(0xFF2E7D32), Color(0xFFE3F1E4),
                    "pH %.2f".format(measurement.ph)
                )
                SensorValueChip(
                    Icons.Default.Bolt, Color(0xFFB26A00), Color(0xFFFFF1D0),
                    "%.2f mS/cm".format(measurement.conductivity)
                )
                SensorValueChip(
                    Icons.Default.WaterDrop, Color(0xFF1565C0), Color(0xFFE3EEFB),
                    "Turb %.0f%%".format(measurement.turbidity)
                )
            }
            if (showMilker && measurement.milkerName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Lechero: ${measurement.milkerName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
