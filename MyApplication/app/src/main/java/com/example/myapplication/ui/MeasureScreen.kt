package com.example.myapplication.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.analysis.ParameterGlossary
import com.example.myapplication.model.analysis.ProgressAnalyzer
import com.example.myapplication.model.analysis.verdict
import com.example.myapplication.ui.components.GlossaryCard
import com.example.myapplication.ui.components.NextStepsCard
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.VerdictCard
import com.example.myapplication.ui.components.displayName
import com.example.myapplication.ui.components.AnalysisSection
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.analysis.MilkAnalyzer
import com.example.myapplication.model.analysis.toAnalysisReading
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.SensorCard
import com.example.myapplication.ui.components.SensorStatus
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

    var phase by remember { mutableStateOf(MeasurePhase.IDLE) }
    var result by remember { mutableStateOf<MilkQualityReading?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    val startMeasuring: () -> Unit = {
        result = null
        startedAt = System.currentTimeMillis()
        measureViewModel.startMeasurement()
        phase = MeasurePhase.MEASURING
    }

    // Se interpreta con las mediciones ya registradas del lote actual (si no hay lote, sin historial).
    val doneReading = result
    val lotId = records.currentLotId
    val previous = remember(records.recent, lotId) {
        if (lotId == null) emptyList() else records.recent.map { it.asAnalysisReading() }
    }
    val analysis = remember(doneReading, previous, lotId, records.ranges) {
        doneReading?.let { MilkAnalyzer.analyze(it.toAnalysisReading(lotId), previous, records.ranges) }
    }
    val verdict = remember(analysis) { analysis?.verdict() }
    val progress = remember(doneReading, previous, lotId, records.ranges) {
        doneReading?.let { ProgressAnalyzer.compare(it.toAnalysisReading(lotId), previous, records.ranges) }
    }

    // Otra muestra o fuente: se vuelve al inicio para empezar una medicion nueva.
    LaunchedEffect(measure.mode, measure.scenario) {
        phase = MeasurePhase.IDLE
        result = null
    }
    // La medicion termina cuando la lectura se estabiliza.
    LaunchedEffect(phase, measure.stability.isStable, measure.reading) {
        val reading = measure.reading
        // Solo cuenta una lectura tomada despues de pulsar el boton (no una anterior).
        if (phase == MeasurePhase.MEASURING && measure.stability.isStable && reading != null && reading.timestamp >= startedAt) {
            result = reading
            phase = MeasurePhase.DONE
        }
    }

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

            when (phase) {
                MeasurePhase.IDLE -> {
                    item {
                        StartMeasurementCard(
                            canStart = measure.mode == MeasureMode.DEMO || measure.isConnected,
                            demo = measure.mode == MeasureMode.DEMO,
                            lotName = records.currentLot?.displayName(),
                            onStart = startMeasuring,
                            onChangeLot = { showLotPicker = true }
                        )
                    }
                }

                MeasurePhase.MEASURING -> {
                    item {
                        MeasuringCard(
                            samples = measure.stability.samples,
                            required = measure.stability.required,
                            onCancel = { phase = MeasurePhase.IDLE }
                        )
                    }
                }

                MeasurePhase.DONE -> {
                    val reading = result
                    if (reading == null || analysis == null || verdict == null) {
                        item { WaitingCard(measure) }
                    } else {
                        val detailed = session.role.canViewAllHistory

                        item { VerdictCard(verdict = verdict, simulated = reading.isSimulated) }
                        item { NextStepsCard(steps = verdict.steps, level = verdict.level) }
                        progress?.let { report -> item { ProgressCard(report) } }
                        item {
                            Text(
                                text = if (reading.isSimulated) "Valores simulados" else "Valores del sensor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        item { SensorGrid(reading, records.ranges) }
                        if (detailed) {
                            item { AnalysisSection(analysis) }
                        }
                        item { GlossaryCard(ParameterGlossary.entries(records.ranges)) }
                        item {
                            CurrentLotCard(
                                currentLot = records.currentLot,
                                saving = records.saving,
                                recordEnabled = measure.canRecord,
                                recordLabel = if (measure.mode == MeasureMode.DEMO) "Registrar medición simulada" else "Registrar medición",
                                hint = measure.recordHint,
                                onChangeLot = { showLotPicker = true },
                                onRecord = {
                                    // Se guarda lo que se mostro mientras siga vigente; si no, la lectura actual de la misma muestra.
                                    val fresh = reading.takeIf { measure.now - it.timestamp <= MeasurementValidator.MAX_READING_AGE_MS }
                                    recordsViewModel.recordMeasurement(fresh ?: measure.reading, measure.stability)
                                }
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = startMeasuring,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Medir de nuevo") }
                        }
                    }
                }
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

private enum class MeasurePhase { IDLE, MEASURING, DONE }

/** Inicio: un boton grande para realizar la medicion. */
@Composable
private fun StartMeasurementCard(
    canStart: Boolean,
    demo: Boolean,
    lotName: String?,
    onStart: () -> Unit,
    onChangeLot: () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Medir la muestra",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (demo) {
                    "Pulse el botón para tomar una medición simulada. Cada medición sale distinta."
                } else {
                    "Ponga el sensor en la muestra y pulse el botón. Mantenga el sensor quieto hasta que termine."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) { Text("Realizar medición", style = MaterialTheme.typography.titleMedium) }
            if (!canStart) {
                Text(
                    text = "Conecte la caja de sensores para medir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lote: ${lotName ?: "sin lote elegido"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onChangeLot) { Text(if (lotName == null) "Elegir lote" else "Cambiar lote") }
            }
        }
    }
}

/** Avance mientras la lectura se estabiliza. */
@Composable
private fun MeasuringCard(samples: Int, required: Int, onCancel: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(
                    text = "Midiendo la muestra…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { if (required == 0) 0f else (samples.toFloat() / required).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Lectura $samples de $required. Mantenga el sensor quieto en la muestra.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
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
