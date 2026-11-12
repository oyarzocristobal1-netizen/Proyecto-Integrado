package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Lot
import com.example.myapplication.data.SavedMeasurement
import java.text.SimpleDateFormat
import java.util.Locale

private val dayTime = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lote activo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = currentLot?.let { "${it.tank} · ${dayOnly.format(it.date)}" } ?: "Sin lote elegido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                TextButton(onClick = onChangeLot, enabled = !saving) {
                    Text(if (currentLot == null) "Elegir" else "Cambiar")
                }
            }
            Button(
                onClick = onRecord,
                enabled = currentLot != null && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar medición")
            }
            if (currentLot == null) {
                Text(
                    text = "Elige o crea el lote de hoy para poder registrar mediciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (canFilter) {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(lots.firstOrNull { it.id == filterLotId }?.tank ?: "Todos los lotes", maxLines = 1)
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

/** Una medicion guardada en el historial. */
@Composable
fun SavedMeasurementRow(
    measurement: SavedMeasurement,
    lotName: String,
    showMilker: Boolean
) {
    val (container, content, label) = when (measurement.status) {
        "verde" -> Triple(Color(0xFFE8F5E9), Color(0xFF1B5E20), "NORMAL")
        "ambar" -> Triple(Color(0xFFFFF3D6), Color(0xFF5C3B00), "PRECAUCIÓN")
        "rojo" -> Triple(Color(0xFFFDE7E7), Color(0xFF7A1212), "FUERA DE RANGO")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "—")
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${dayTime.format(measurement.dateTime)} · $lotName",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "T %.1f°C · pH %.2f · CE %.2f mS/cm · Turb %.0f%%".format(
                        measurement.temperature, measurement.ph, measurement.conductivity, measurement.turbidity
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showMilker && measurement.milkerName.isNotBlank()) {
                    Text(
                        text = "Lechero: ${measurement.milkerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = container) {
                Text(
                    text = label,
                    color = content,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
