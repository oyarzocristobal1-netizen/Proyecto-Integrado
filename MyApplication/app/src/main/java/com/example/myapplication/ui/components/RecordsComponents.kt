package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Lot
import com.example.myapplication.data.SavedMeasurement
import com.example.myapplication.data.Tank
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.StatusTone
import java.text.SimpleDateFormat
import java.util.Locale

private val dayTime = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
private val dayOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

/** Nombre del lote con su fecha, para listas y menus. */
fun Lot.displayName(): String = "$tank · ${dayOnly.format(date)}" + if (closed) " (cerrado)" else ""

/** Texto del semaforo guardado en Firestore. */
fun statusLabel(status: String): String = when (status) {
    "verde" -> "CONFORME"
    "ambar" -> "PRECAUCIÓN"
    "rojo" -> "FUERA DE RANGO"
    else -> "SIN ESTADO"
}

/**
 * Lote activo y boton para registrar la lectura actual. El boton solo se habilita cuando
 * hay lote y la lectura es reciente y estable; si no, se explica el motivo debajo.
 */
@Composable
fun CurrentLotCard(
    currentLot: Lot?,
    saving: Boolean,
    recordEnabled: Boolean,
    recordLabel: String,
    hint: String?,
    onChangeLot: () -> Unit,
    onRecord: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                        text = currentLot?.displayName() ?: "Sin lote elegido",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                OutlinedButton(
                    onClick = onChangeLot,
                    enabled = !saving,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(if (currentLot == null) "Elegir" else "Cambiar", fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onRecord,
                enabled = currentLot != null && recordEnabled && !saving,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(recordLabel, fontWeight = FontWeight.SemiBold)
            }
            val note = when {
                currentLot == null -> "Elige o crea el lote de hoy para poder registrar mediciones."
                else -> hint
            }
            if (note != null) {
                Text(
                    text = note,
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
    /** Estanques activos del catalogo. Si esta vacio, se escribe el nombre a mano. */
    tanks: List<Tank>,
    currentLotId: String?,
    saving: Boolean,
    onSelect: (String) -> Unit,
    onCreate: (tank: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tank by rememberSaveable { mutableStateOf("") }
    val useCatalog = tanks.isNotEmpty()
    val tankError = if (tank.isBlank()) null else MeasurementValidator.validateTankName(tank)
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
                if (useCatalog) {
                    tanks.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = tank == t.name,
                                    enabled = !saving,
                                    role = Role.RadioButton,
                                    onClick = { tank = t.name }
                                )
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = tank == t.name, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = tank,
                        onValueChange = { if (it.length <= 40) tank = it },
                        label = { Text("Estanque") },
                        placeholder = { Text("Ej.: Estanque 1") },
                        singleLine = true,
                        enabled = !saving,
                        isError = tankError != null,
                        supportingText = { Text(tankError ?: "${tank.trim().length}/40") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Se crea un lote por estanque y día. Si ya existe, se usa el mismo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(tank) }, enabled = !saving && tank.isNotBlank() && tankError == null) {
                Text(if (saving) "Creando…" else "Crear y usar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cerrar") }
        }
    )
}

/** Valor de un sensor con su icono de color (historial). */
@Composable
private fun SensorValueChip(icon: ImageVector, tone: StatusTone, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(tone.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Una medicion guardada: fecha, lote, semaforo, origen, sincronizacion y los 4 valores. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedMeasurementRow(
    measurement: SavedMeasurement,
    lotName: String,
    showMilker: Boolean,
    /** Si se entrega, se muestra el boton "Anular" (solo encargado y administrador). */
    onAnnul: (() -> Unit)? = null,
    /** Si se entrega, se muestra "Eliminar" (solo administrador; ultimo recurso). */
    onDelete: (() -> Unit)? = null
) {
    val status = AppTheme.status
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (measurement.annulled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (measurement.annulled) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${dayTime.format(measurement.dateTime)} · $lotName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (measurement.annulled) {
                    StatusPill("ANULADA", status.alert)
                } else {
                    StatusPill(statusLabel(measurement.status), status.forStatus(measurement.status))
                }
                if (measurement.isSimulated) SimulatedBadge()
                if (measurement.pendingSync) PendingSyncBadge()
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SensorValueChip(Icons.Default.Thermostat, status.alert, "%.1f °C".format(measurement.temperature))
                SensorValueChip(Icons.Default.Science, status.good, "pH %.2f".format(measurement.ph))
                SensorValueChip(Icons.Default.Bolt, status.warning, "%.2f mS/cm".format(measurement.conductivity))
                SensorValueChip(Icons.Default.WaterDrop, status.info, "Turb. %.0f %%".format(measurement.turbidity))
            }
            if (showMilker && measurement.milkerName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Lechero: ${measurement.milkerName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (measurement.annulled) {
                Text(
                    text = buildString {
                        append("Anulada")
                        if (measurement.annulledByName.isNotBlank()) append(" por ${measurement.annulledByName}")
                        measurement.annulledAt?.let { append(" el ${dayTime.format(it)}") }
                        append(". No cuenta en análisis ni reportes.")
                        if (measurement.annulReason.isNotBlank()) append("\nMotivo: ${measurement.annulReason}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if ((!measurement.annulled && onAnnul != null) || onDelete != null) {
                Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!measurement.annulled && onAnnul != null) {
                        TextButton(onClick = onAnnul, enabled = !measurement.pendingSync) {
                            Text("Anular", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, enabled = !measurement.pendingSync) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
