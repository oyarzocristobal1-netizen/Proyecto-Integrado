package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.Calibration
import com.example.myapplication.data.Device
import com.example.myapplication.model.CalibrationParameter
import com.example.myapplication.model.CrudRules
import com.example.myapplication.model.RecordRules
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.StatusPill
import com.example.myapplication.ui.components.TextInputDialog
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.DevicesViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dispositivos y calibraciones (solo administrador). Lista de dispositivos y, al elegir uno,
 * su historial de calibraciones. Los dispositivos se desactivan, no se borran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    session: UserSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Device?>(null) }
    var calibrating by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Calibration?>(null) }
    val selected = state.selected

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    // Atras en el detalle vuelve a la lista; atras en la lista sale de la pantalla.
    BackHandler(enabled = selected != null) { viewModel.selectDevice(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    ScreenTitle(
                        if (selected != null) selected.name else "Dispositivos",
                        if (selected != null) "Calibraciones" else "${state.devices.size} registrado(s)"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) viewModel.selectDevice(null) else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { editing = selected }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar dispositivo")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selected == null) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo dispositivo") }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (selected == null) {
                item {
                    Text(
                        "Cajas de sensores y el simulador de la app. Un dispositivo no se borra: se desactiva " +
                            "para que las mediciones antiguas sigan asociadas a él.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.loading) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                } else if (state.devices.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Sensors,
                            title = "Sin dispositivos",
                            message = "Registra la caja de sensores para poder asociarle mediciones y calibraciones.",
                            actionLabel = "Nuevo dispositivo",
                            onAction = { creating = true }
                        )
                    }
                }
                items(state.devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        lastCalibration = device.lastCalibration?.let { dateFormat.format(it) },
                        onClick = { viewModel.selectDevice(device.id) }
                    )
                }
            } else {
                item {
                    DeviceSummary(selected, selected.lastCalibration?.let { dateFormat.format(it) })
                }
                item {
                    Column {
                        Button(
                            onClick = { calibrating = true },
                            enabled = selected.active && !state.saving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Registrar calibración") }
                        if (!selected.active) {
                            Text(
                                "Activa el dispositivo para registrar una calibración.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                if (state.calibrationsLoading && state.calibrations.isEmpty()) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                } else if (state.calibrations.isEmpty()) {
                    item {
                        Text(
                            "Todavía no hay calibraciones registradas para este dispositivo.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(state.calibrations, key = { it.id }) { c ->
                    CalibrationCard(c, dateFormat.format(c.date), onEditNote = { editingNote = c })
                }
            }
        }
    }

    if (creating) {
        DeviceFormDialog(
            device = null,
            saving = state.saving,
            onConfirm = { name, location, _ -> viewModel.createDevice(name, location) { creating = false } },
            onDismiss = { creating = false }
        )
    }
    editing?.let { device ->
        DeviceFormDialog(
            device = device,
            saving = state.saving,
            onConfirm = { name, location, active ->
                viewModel.updateDevice(device, name, location, active) { editing = null }
            },
            onDismiss = { editing = null }
        )
    }
    if (calibrating && selected != null) {
        CalibrationDialog(
            deviceName = selected.name,
            saving = state.saving,
            onConfirm = { parameter, note ->
                viewModel.addCalibration(selected, parameter, note, session) { calibrating = false }
            },
            onDismiss = { calibrating = false }
        )
    }
    editingNote?.let { c ->
        TextInputDialog(
            title = "Nota de la calibración",
            label = "Nota",
            initial = c.note,
            maxLength = CrudRules.CALIBRATION_NOTE_MAX,
            confirmLabel = "Guardar",
            saving = state.saving,
            validate = CrudRules::validateCalibrationNote,
            stateKey = c.id,
            onConfirm = { note -> viewModel.updateCalibrationNote(c, note) { editingNote = null } },
            onDismiss = { editingNote = null }
        )
    }
}

@Composable
private fun DeviceCard(device: Device, lastCalibration: String?, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DeviceSummary(device, lastCalibration)
        }
    }
}

@Composable
private fun DeviceSummary(device: Device, lastCalibration: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (device.active) StatusPill("ACTIVO", AppTheme.status.good) else StatusPill("INACTIVO", AppTheme.status.warning)
        }
        Text(
            "ID: ${device.id}" + if (device.id == RecordRules.SIMULATOR_DEVICE_ID) " · genera datos inventados (modo DEMO)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (device.location.isNotBlank()) {
            Text("Ubicación: ${device.location}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (lastCalibration != null) "Última calibración: $lastCalibration" else "Sin calibraciones registradas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalibrationCard(c: Calibration, dateText: String, onEditNote: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.parameter.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "$dateText · ${c.doneBy.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (c.note.isNotBlank()) Text("Nota: ${c.note}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onEditNote, modifier = Modifier.align(Alignment.End)) {
                Text(if (c.note.isBlank()) "Agregar nota" else "Editar nota")
            }
        }
    }
}

/** Crear (device == null) o editar un dispositivo. El ID sale del nombre al crearlo y no cambia. */
@Composable
private fun DeviceFormDialog(
    device: Device?,
    saving: Boolean,
    onConfirm: (name: String, location: String, active: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(device?.id) { mutableStateOf(device?.name.orEmpty()) }
    var location by rememberSaveable(device?.id) { mutableStateOf(device?.location.orEmpty()) }
    var active by rememberSaveable(device?.id) { mutableStateOf(device?.active ?: true) }
    val nameError = if (name.isEmpty()) null else CrudRules.validateDeviceName(name)
    val locationError = CrudRules.validateDeviceLocation(location)
    val valid = CrudRules.validateDeviceName(name) == null && locationError == null
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (device == null) "Nuevo dispositivo" else "Editar dispositivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= CrudRules.DEVICE_NAME_MAX) name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej.: Caja de sensores 2") },
                    singleLine = true,
                    enabled = !saving,
                    isError = nameError != null,
                    supportingText = {
                        Text(
                            nameError
                                ?: if (device == null && name.isNotBlank()) "ID: ${CrudRules.deviceIdFor(name)}" else "${name.length}/${CrudRules.DEVICE_NAME_MAX}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { if (it.length <= CrudRules.DEVICE_LOCATION_MAX) location = it },
                    label = { Text("Ubicación (opcional)") },
                    placeholder = { Text("Ej.: Sala de ordeña") },
                    singleLine = true,
                    enabled = !saving,
                    isError = locationError != null,
                    supportingText = { Text(locationError ?: "${location.length}/${CrudRules.DEVICE_LOCATION_MAX}") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (device != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activo", modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it }, enabled = !saving)
                    }
                    Text(
                        "El ID no cambia. Un dispositivo desactivado no se puede calibrar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, location, active) }, enabled = !saving && valid) {
                Text(if (saving) "Guardando…" else if (device == null) "Crear" else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}

/** Registrar una calibracion: que sensor y una nota opcional. La fecha y el responsable los pone el sistema. */
@Composable
private fun CalibrationDialog(
    deviceName: String,
    saving: Boolean,
    onConfirm: (CalibrationParameter, String) -> Unit,
    onDismiss: () -> Unit
) {
    var parameter by rememberSaveable { mutableStateOf(CalibrationParameter.ALL) }
    var note by rememberSaveable { mutableStateOf("") }
    val noteError = CrudRules.validateCalibrationNote(note)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Registrar calibración") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "$deviceName. Queda registrada con la fecha de hoy y tu nombre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CalibrationParameter.entries.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = parameter == p,
                                enabled = !saving,
                                role = Role.RadioButton,
                                onClick = { parameter = p }
                            )
                    ) {
                        RadioButton(selected = parameter == p, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(p.label)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= CrudRules.CALIBRATION_NOTE_MAX) note = it },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Ej.: calibrado con solución patrón") },
                    enabled = !saving,
                    isError = noteError != null,
                    supportingText = { Text(noteError ?: "${note.length}/${CrudRules.CALIBRATION_NOTE_MAX}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parameter, note) }, enabled = !saving && noteError == null) {
                Text(if (saving) "Guardando…" else "Registrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}
