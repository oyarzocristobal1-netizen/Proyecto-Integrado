package com.example.myapplication.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.PhStatus
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.TemperatureStatus
import com.example.myapplication.model.UserSession
import com.example.myapplication.sensor.DemoScenario
import com.example.myapplication.ui.components.CurrentLotCard
import com.example.myapplication.ui.components.DemoModeBanner
import com.example.myapplication.ui.components.DeviceSelectionDialog
import com.example.myapplication.ui.components.LotPickerDialog
import com.example.myapplication.ui.components.NoticeBanner
import com.example.myapplication.ui.components.OfflineBanner
import com.example.myapplication.ui.components.QualityAssessmentCard
import com.example.myapplication.ui.components.AnalysisSection
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.analysis.MilkAnalyzer
import com.example.myapplication.model.analysis.toAnalysisReading
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.SensorCard
import com.example.myapplication.ui.components.SensorStatus
import com.example.myapplication.ui.components.SimulatedBadge
import com.example.myapplication.ui.components.StatusPill
import com.example.myapplication.ui.components.TemperatureScale
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.MeasureMode
import com.example.myapplication.viewmodel.MeasureUiState
import com.example.myapplication.viewmodel.MeasureViewModel
import com.example.myapplication.viewmodel.RecordsViewModel

/**
 * Tomar una medicion (CU1): lectura en vivo (simulada o de la caja), su evaluacion y el registro
 * en el lote activo. El boton Registrar solo se habilita con una lectura reciente y estable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    measureViewModel: MeasureViewModel,
    recordsViewModel: RecordsViewModel,
    session: UserSession,
    modifier: Modifier = Modifier
) {
    val measure by measureViewModel.state.collectAsStateWithLifecycle()
    val records by recordsViewModel.state.collectAsStateWithLifecycle()
    var showLotPicker by rememberSaveable { mutableStateOf(false) }

    // La simulacion y el reloj solo corren mientras esta pantalla esta visible.
    LifecycleStartEffect(measureViewModel) {
        measureViewModel.onScreenVisible(true)
        onStopOrDispose { measureViewModel.onScreenVisible(false) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Medir", if (measure.mode == MeasureMode.DEMO) "Modo DEMO (simulación)" else "Caja de sensores") },
                actions = {
                    if (measure.mode == MeasureMode.DEVICE) {
                        IconButton(onClick = measureViewModel::openDeviceDialog) {
                            Icon(Icons.Default.Bluetooth, contentDescription = "Elegir caja de sensores")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!records.online) {
                item { OfflineBanner(records.pendingSyncCount) }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MeasureMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = measure.mode == mode,
                            onClick = { measureViewModel.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MeasureMode.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            when (measure.mode) {
                MeasureMode.DEMO -> {
                    item { DemoModeBanner() }
                    item { ScenarioSelector(selected = measure.scenario, onSelect = measureViewModel::setScenario) }
                }
                MeasureMode.DEVICE -> item {
                    DeviceCard(
                        state = measure,
                        onConnect = measureViewModel::openDeviceDialog,
                        onDisconnect = measureViewModel::disconnect
                    )
                }
            }

            val reading = measure.reading
            if (reading == null) {
                item { WaitingCard(measure) }
            } else {
                item { ReadingStatusRow(measure, reading) }
                item {
                    QualityAssessmentCard(assessment = reading.overallAssessment, simulated = reading.isSimulated)
                }
                item {
                    // Se interpreta con las mediciones ya registradas del lote actual (si no hay lote, sin historial).
                    val lotId = records.currentLotId
                    val analysis = remember(reading, records.recent, lotId, records.ranges, measure.stability) {
                        MilkAnalyzer.analyze(
                            current = reading.toAnalysisReading(lotId),
                            previous = if (lotId == null) emptyList() else records.recent.map { it.asAnalysisReading() },
                            ranges = records.ranges,
                            stability = measure.stability
                        )
                    }
                    AnalysisSection(analysis)
                }
                item {
                    Text(
                        text = if (reading.isSimulated) "Valores simulados" else "Valores del sensor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item { SensorGrid(reading, records.ranges) }
            }

            item {
                CurrentLotCard(
                    currentLot = records.currentLot,
                    saving = records.saving,
                    recordEnabled = measure.canRecord,
                    recordLabel = if (measure.mode == MeasureMode.DEMO) "Registrar medición simulada" else "Registrar medición",
                    hint = measure.recordHint,
                    onChangeLot = { showLotPicker = true },
                    onRecord = { recordsViewModel.recordMeasurement(measure.reading, measure.stability) }
                )
            }

            item {
                Text(
                    if (session.role.canViewAllHistory) {
                        "Las mediciones registradas aparecen en Historial y, si están fuera de rango, generan alertas."
                    } else {
                        "Tus mediciones registradas aparecen en Historial y, si están fuera de rango, generan alertas."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (measure.showDeviceDialog) {
        DeviceSelectionDialog(
            pairedDevices = measure.pairedDevices,
            onSelectDevice = measureViewModel::connect,
            onRefreshDevices = measureViewModel::refreshDevices,
            onDismiss = measureViewModel::closeDeviceDialog
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
}

/** Muestras de prueba del modo DEMO. */
@Composable
private fun ScenarioSelector(selected: DemoScenario, onSelect: (DemoScenario) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Muestra simulada", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DemoScenario.entries.forEach { scenario ->
                FilterChip(
                    selected = selected == scenario,
                    onClick = { onSelect(scenario) },
                    shape = RoundedCornerShape(50),
                    label = { Text(scenario.label) }
                )
            }
        }
        Text(
            selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Estado de la conexion con la caja por Bluetooth. */
@Composable
private fun DeviceCard(state: MeasureUiState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val status = AppTheme.status
    val connection = state.connection
    val (tone, text) = when (connection) {
        is BluetoothConnectionState.Connected -> status.good to "Conectado a ${connection.deviceName}"
        is BluetoothConnectionState.Connecting -> status.info to "Conectando con la caja…"
        is BluetoothConnectionState.Error -> status.alert to connection.message
        is BluetoothConnectionState.Disconnected -> status.warning to "Caja desconectada"
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (connection is BluetoothConnectionState.Connected) Icons.Outlined.Sensors else Icons.Outlined.BluetoothDisabled,
                    contentDescription = null,
                    tint = tone.content
                )
                Text(text, color = tone.content, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (connection is BluetoothConnectionState.Connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            when (connection) {
                is BluetoothConnectionState.Connected -> OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                is BluetoothConnectionState.Connecting -> Unit
                else -> Button(onClick = onConnect) { Text(if (connection is BluetoothConnectionState.Error) "Reintentar" else "Conectar caja") }
            }
            if (state.rejectedFrames > 0) {
                Text(
                    "Lecturas descartadas por datos incompletos o fuera de límites: ${state.rejectedFrames}." +
                        (state.lastRejectReason?.let { " Última: $it" } ?: ""),
                    color = tone.content,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Conexión por Bluetooth clásico con la caja emparejada. Pendiente de validar con la caja real: " +
                    "formato de datos, conexión y calibración.",
                color = tone.content,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Mientras no llega ninguna lectura: no se muestran valores inventados. */
@Composable
private fun WaitingCard(state: MeasureUiState) {
    val waitingDevice = state.mode == MeasureMode.DEVICE
    NoticeBanner(
        title = if (waitingDevice && !state.isConnected) "Sin lecturas" else "Esperando lectura",
        text = when {
            waitingDevice && !state.isConnected -> "Conecta la caja de sensores para ver la calidad de la leche en vivo."
            waitingDevice -> "La caja está conectada. Las lecturas aparecerán aquí apenas lleguen."
            else -> "Generando la primera lectura simulada…"
        },
        tone = AppTheme.status.info,
        icon = Icons.Outlined.Info
    )
}

/** Antiguedad, estabilidad y origen de la lectura mostrada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingStatusRow(state: MeasureUiState, reading: MilkQualityReading) {
    val status = AppTheme.status
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (reading.isSimulated) SimulatedBadge()
        if (state.stability.isStable) {
            StatusPill("Lectura estable", status.good, icon = Icons.Outlined.CheckCircle)
        } else {
            StatusPill(
                "Estabilizando ${state.stability.samples}/${state.stability.required}",
                status.warning,
                icon = Icons.Outlined.HourglassTop
            )
        }
        val age = state.readingAgeSeconds ?: 0L
        StatusPill(
            text = if (age <= 1) "Recién leída" else "Hace $age s",
            tone = if (state.isReadingFresh) status.info else status.alert
        )
    }
}

/** Las 4 tarjetas de sensores en dos filas. */
@Composable
private fun SensorGrid(reading: MilkQualityReading, ranges: QualityRanges) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val tempStatus = reading.temperatureStatus
            SensorCard(
                title = "Temperatura",
                valueString = "%.1f".format(reading.temperature),
                unit = "°C",
                statusText = tempStatus.label,
                status = when (tempStatus) {
                    TemperatureStatus.COLD -> SensorStatus.GOOD
                    TemperatureStatus.AMBIENT -> SensorStatus.WARNING
                    TemperatureStatus.HIGH -> SensorStatus.ALERT
                },
                icon = Icons.Default.Thermostat,
                referenceRange = "≤ %.1f °C".format(ranges.tempMax),
                progress = (reading.temperature / 30.0f).coerceIn(0f, 1f),
                progressBrush = TemperatureScale,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            val phStatus = reading.phStatus
            SensorCard(
                title = "pH",
                valueString = "%.2f".format(reading.ph),
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
                referenceRange = "%.2f – %.2f".format(ranges.phMin, ranges.phMax),
                progress = ((reading.ph - 5.0f) / 4.0f).coerceIn(0f, 1f),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val condOk = reading.conductivity in ranges.condMin..ranges.condMax
            SensorCard(
                title = "Conductividad",
                valueString = "%.2f".format(reading.conductivity),
                unit = "mS/cm",
                statusText = if (condOk) "NORMAL" else "REVISAR",
                status = if (condOk) SensorStatus.GOOD else SensorStatus.WARNING,
                icon = Icons.Default.ElectricBolt,
                referenceRange = "%.2f – %.2f".format(ranges.condMin, ranges.condMax),
                progress = (reading.conductivity / 8.0f).coerceIn(0f, 1f),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            val turbOk = reading.turbidity >= ranges.turbMin
            SensorCard(
                title = "Turbidez",
                valueString = "%.1f".format(reading.turbidity),
                unit = "%",
                statusText = if (turbOk) "OPACA" else "DILUIDA",
                status = if (turbOk) SensorStatus.GOOD else SensorStatus.ALERT,
                icon = Icons.Default.WaterDrop,
                referenceRange = "≥ %.1f %%".format(ranges.turbMin),
                progress = (reading.turbidity / 100.0f).coerceIn(0f, 1f),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}
