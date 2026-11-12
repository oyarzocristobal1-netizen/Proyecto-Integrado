package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.model.PhStatus
import com.example.myapplication.model.UserSession
import com.example.myapplication.model.TemperatureStatus
import com.example.myapplication.ui.components.*
import com.example.myapplication.viewmodel.DemoScenario
import com.example.myapplication.viewmodel.MilkQualityViewModel
import com.example.myapplication.viewmodel.RecordsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkQualityScreen(
    viewModel: MilkQualityViewModel,
    recordsViewModel: RecordsViewModel,
    session: UserSession,
    onLogout: () -> Unit,
    onOpenUserManagement: () -> Unit,
    onOpenLots: () -> Unit,
    modifier: Modifier = Modifier
) {
    val role = session.role
    val records by recordsViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLotPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(records.message) {
        val message = records.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            recordsViewModel.messageShown()
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val currentReading = uiState.currentReading

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Calidad de Leche",
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (uiState.isDemoMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (uiState.isDemoMode) "DEMO" else "BT SERIE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = if (uiState.isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                actions = {
                    // Configuracion y codigo de la caja: solo quien puede calibrar el dispositivo (administrador)
                    if (role.canCalibrateDevice) {
                        IconButton(onClick = { viewModel.setShowCodeGuideDialog(true) }) {
                            Icon(Icons.Default.Code, contentDescription = "Código Arduino")
                        }
                    }
                    IconButton(onClick = { viewModel.setShowDeviceDialog(true) }) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Conectar Bluetooth")
                    }
                    if (role.canEditLots) {
                        IconButton(onClick = onOpenLots) {
                            Icon(Icons.Default.Inventory2, contentDescription = "Lotes")
                        }
                    }
                    if (role.canManageUsers) {
                        IconButton(onClick = onOpenUserManagement) {
                            Icon(Icons.Default.ManageAccounts, contentDescription = "Gestión de usuarios")
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item 0: Usuario con sesion iniciada y su rol
            item {
                UserRoleCard(session = session)
            }

            // Item 1: Bluetooth Connection Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.connectionState) {
                            is BluetoothConnectionState.Connected -> Color(0xFFE8F5E9)
                            is BluetoothConnectionState.Connecting -> Color(0xFFFFFDE7)
                            is BluetoothConnectionState.Error -> Color(0xFFFFEBEE)
                            is BluetoothConnectionState.Disconnected -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            uiState.isDemoMode -> Color(0xFF0288D1)
                                            uiState.connectionState is BluetoothConnectionState.Connected -> Color(0xFF2E7D32)
                                            uiState.connectionState is BluetoothConnectionState.Connecting -> Color(0xFFF57F17)
                                            uiState.connectionState is BluetoothConnectionState.Error -> Color(0xFFC62828)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Text(
                                text = when (val state = uiState.connectionState) {
                                    is BluetoothConnectionState.Connected -> "Conectado a ${state.deviceName}"
                                    is BluetoothConnectionState.Connecting -> "Conectando al sensor..."
                                    is BluetoothConnectionState.Error -> "Error: ${state.message}"
                                    is BluetoothConnectionState.Disconnected -> if (uiState.isDemoMode) "Modo Simulación Activo" else "Desconectado"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = uiState.isDemoMode,
                                onClick = { viewModel.toggleDemoMode(!uiState.isDemoMode) },
                                label = { Text("Simulación") },
                                leadingIcon = {
                                    if (uiState.isDemoMode) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )

                            if (uiState.connectionState is BluetoothConnectionState.Connected) {
                                TextButton(onClick = { viewModel.disconnectBluetooth() }) {
                                    Text("Desconectar")
                                }
                            } else if (!uiState.isDemoMode) {
                                Button(
                                    onClick = { viewModel.setShowDeviceDialog(true) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Conectar")
                                }
                            }
                        }
                    }
                }
            }

            // Item 2: Demo Scenario Selector (when in Demo Mode)
            if (uiState.isDemoMode) {
                item {
                    Column {
                        Text(
                            text = "Seleccionar Muestra de Prueba:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DemoScenario.entries.forEach { scenario ->
                                val isSelected = uiState.selectedScenario == scenario
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setDemoScenario(scenario) },
                                    label = {
                                        Text(
                                            text = when (scenario) {
                                                DemoScenario.NORMAL -> "Normal"
                                                DemoScenario.ACIDIC_BACTERIAL -> "Ácida"
                                                DemoScenario.ALKALINE_MASTITIS -> "Mastitis"
                                                DemoScenario.DILUTED_WATER -> "Diluida"
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Item 3: Overall Diagnostic Assessment Banner Card
            item {
                QualityAssessmentCard(assessment = currentReading.overallAssessment)
            }

            // Item 4: Grid Title
            item {
                Text(
                    text = "Mediciones de Sensores en Tiempo Real",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Item 5: Row 1 - Temperature & pH Cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Temperatura Card
                    val tempStatus = currentReading.temperatureStatus
                    SensorCard(
                        title = "Temperatura",
                        valueString = "%.1f".format(currentReading.temperature),
                        unit = "°C",
                        statusText = tempStatus.label,
                        status = when (tempStatus) {
                            TemperatureStatus.COLD -> SensorStatus.GOOD
                            TemperatureStatus.AMBIENT -> SensorStatus.WARNING
                            TemperatureStatus.HIGH -> SensorStatus.ALERT
                        },
                        icon = Icons.Default.Thermostat,
                        referenceRange = "≤ 4°C (frío)",
                        progress = (currentReading.temperature / 30.0f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    // pH Card
                    val phStatus = currentReading.phStatus
                    SensorCard(
                        title = "pH",
                        valueString = "%.2f".format(currentReading.ph),
                        unit = "pH",
                        statusText = when (phStatus) {
                            PhStatus.NORMAL -> "NORMAL"
                            PhStatus.ACIDIC -> "ÁCIDO"
                            PhStatus.ALKALINE -> "ALCALINO"
                        },
                        status = when (phStatus) {
                            PhStatus.NORMAL -> SensorStatus.GOOD
                            PhStatus.ACIDIC -> SensorStatus.ALERT
                            PhStatus.ALKALINE -> SensorStatus.WARNING
                        },
                        icon = Icons.Default.Science,
                        referenceRange = "6.60 - 6.80",
                        progress = ((currentReading.ph - 5.0f) / 4.0f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Item 6: Row 2 - Conductividad & Turbidez Cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Conductividad Card
                    val condOk = currentReading.conductivity in 4.0f..6.0f
                    SensorCard(
                        title = "Conductividad",
                        valueString = "%.2f".format(currentReading.conductivity),
                        unit = "mS/cm",
                        statusText = if (condOk) "NORMAL" else "REVISAR",
                        status = if (condOk) SensorStatus.GOOD else SensorStatus.WARNING,
                        icon = Icons.Default.ElectricBolt,
                        referenceRange = "4.00 - 5.50 mS/cm",
                        progress = (currentReading.conductivity / 8.0f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    // Turbidez Card
                    val turbOk = currentReading.turbidity >= 50.0f
                    SensorCard(
                        title = "Turbidez",
                        valueString = "%.1f".format(currentReading.turbidity),
                        unit = "%",
                        statusText = if (turbOk) "OPACA" else "DILUIDA",
                        status = if (turbOk) SensorStatus.GOOD else SensorStatus.ALERT,
                        icon = Icons.Default.WaterDrop,
                        referenceRange = "> 50.0 %",
                        progress = currentReading.turbidity / 100.0f,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Item 7: Lote activo y boton para registrar la lectura actual
            item {
                CurrentLotCard(
                    currentLot = records.currentLot,
                    saving = records.saving,
                    onChangeLot = { showLotPicker = true },
                    onRecord = { recordsViewModel.recordMeasurement(currentReading) }
                )
            }

            // Item 8: Historial guardado en Firestore (segun el rol)
            item {
                HistoryHeader(
                    title = if (role.canViewAllHistory) "Historial de Mediciones" else "Mis Mediciones",
                    count = records.history.size,
                    canFilter = role.canViewAllHistory,
                    lots = records.lots,
                    filterLotId = records.historyLotFilter,
                    onFilter = { recordsViewModel.setHistoryFilter(it) }
                )
            }

            if (records.history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (records.historyLoading) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                "Sin mediciones registradas aún.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(records.history, key = { it.id }) { measurement ->
                    SavedMeasurementRow(
                        measurement = measurement,
                        lotName = records.lotName(measurement.lotId),
                        showMilker = role.canViewAllHistory
                    )
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialogs
    if (uiState.showDeviceDialog) {
        DeviceSelectionDialog(
            pairedDevices = uiState.pairedDevices,
            onSelectDevice = { address -> viewModel.connectDevice(address) },
            onRefreshDevices = { viewModel.refreshDevices() },
            onDismiss = { viewModel.setShowDeviceDialog(false) }
        )
    }

    if (showLotPicker) {
        LotPickerDialog(
            todayLots = records.todayLots,
            currentLotId = records.currentLotId,
            saving = records.saving,
            onSelect = {
                recordsViewModel.selectLot(it)
                showLotPicker = false
            },
            onCreate = { tank -> recordsViewModel.openTodayLot(tank) { showLotPicker = false } },
            onDismiss = { showLotPicker = false }
        )
    }

    if (uiState.showCodeGuideDialog) {
        ArduinoCodeDialog(
            onDismiss = { viewModel.setShowCodeGuideDialog(false) }
        )
    }
}

/** Tarjeta con el nombre del usuario, su rol y lo que ese rol puede hacer (Tabla 31). */
@Composable
private fun UserRoleCard(session: UserSession) {
    val role = session.role
    val permissions = buildList {
        add("medir")
        add(if (role.canViewAllHistory) "ver todo el historial" else "ver sus mediciones")
        if (role.canEditLots) add("editar lotes")
        if (role.canAttendAlerts) add("atender alertas")
        if (role.canExportReports) add("exportar reportes")
        if (role.canCalibrateDevice) add("calibrar")
        if (role.canManageUsers) add("gestionar usuarios")
        if (role.canEditQualityRanges) add("cambiar rangos")
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = role.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Puede: " + permissions.joinToString(", ") + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
