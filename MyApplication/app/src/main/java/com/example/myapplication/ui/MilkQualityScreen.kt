package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.model.PhStatus
import com.example.myapplication.ui.components.*
import com.example.myapplication.viewmodel.DemoScenario
import com.example.myapplication.viewmodel.MilkQualityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkQualityScreen(
    viewModel: MilkQualityViewModel,
    modifier: Modifier = Modifier
) {
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
                    IconButton(onClick = { viewModel.setShowCodeGuideDialog(true) }) {
                        Icon(Icons.Default.Code, contentDescription = "Código Arduino")
                    }
                    IconButton(onClick = { viewModel.setShowDeviceDialog(true) }) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Conectar Bluetooth")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Temperatura Card
                    SensorCard(
                        title = "Temperatura",
                        valueString = "%.1f".format(currentReading.temperature),
                        unit = "°C",
                        statusText = if (currentReading.temperature in 10f..25f || currentReading.temperature <= 4f) "OK" else "ALTA",
                        status = if (currentReading.temperature in 10f..25f || currentReading.temperature <= 4f) SensorStatus.GOOD else SensorStatus.WARNING,
                        icon = Icons.Default.Thermostat,
                        referenceRange = "< 4°C (Frío) / 15-25°C",
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Item 6: Row 2 - Conductividad & Turbidez Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Item 7: History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historial de Mediciones (${uiState.history.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.history.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Limpiar")
                        }
                    }
                }
            }

            // Item 8: History List Items
            if (uiState.history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sin lecturas registradas aún.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.history.take(15)) { reading ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = reading.formattedTime,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "T: %.1f°C  |  CE: %.1fmS  |  Turb: %.0f%%".format(reading.temperature, reading.conductivity, reading.turbidity),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (reading.phStatus) {
                                    PhStatus.NORMAL -> Color(0xFFE8F5E9)
                                    PhStatus.ACIDIC -> Color(0xFFFFEBEE)
                                    PhStatus.ALKALINE -> Color(0xFFFFFDE7)
                                }
                            ) {
                                Text(
                                    text = "pH %.2f".format(reading.ph),
                                    fontWeight = FontWeight.Bold,
                                    color = when (reading.phStatus) {
                                        PhStatus.NORMAL -> Color(0xFF2E7D32)
                                        PhStatus.ACIDIC -> Color(0xFFC62828)
                                        PhStatus.ALKALINE -> Color(0xFFF57F17)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
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

    if (uiState.showCodeGuideDialog) {
        ArduinoCodeDialog(
            onDismiss = { viewModel.setShowCodeGuideDialog(false) }
        )
    }
}
