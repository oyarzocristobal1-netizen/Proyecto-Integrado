package com.example.myapplication.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Lot
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.analysis.LotAnalyzer
import com.example.myapplication.model.analysis.verdict
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.SaleDialog
import com.example.myapplication.ui.components.SalePill
import com.example.myapplication.ui.components.saleDetailText
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.StatusPill
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.RecordsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Lotes recientes; el encargado y el administrador pueden editar el estanque y la observacion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotsScreen(
    viewModel: RecordsViewModel,
    /** Solo el administrador elimina de verdad (lo normal es cerrar el lote). */
    canDelete: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Lot?>(null) }
    var togglingClosed by remember { mutableStateOf<Lot?>(null) }
    var deleting by remember { mutableStateOf<Lot?>(null) }
    var releasing by remember { mutableStateOf<Lot?>(null) }
    // Analisis de cada lote con sus mediciones validas (las anuladas no cuentan).
    val measurements = state.recentValid + state.historyValid
    val analyses = remember(measurements, state.ranges) {
        LotAnalyzer.analyze(measurements.distinctBy { it.id }.map { it.asAnalysisReading() }, state.ranges, maxLots = Int.MAX_VALUE)
            .associateBy { it.lotId }
    }
    val simulatedLotIds = remember(measurements) {
        measurements.filter { it.isSimulated }.map { it.lotId }.toSet()
    }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Lotes", "${state.lots.size} recientes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "Toca un lote para corregir el estanque, agregar una observación o cerrarlo. " +
                        "Con \"Liberar para venta\" decides si la leche del lote es apta o no. " +
                        "Un lote cerrado no admite mediciones nuevas (se puede reabrir). Eliminar un lote es el último recurso (solo el administrador).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.lots.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay lotes.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(state.lots, key = { it.id }) { lot ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = lot }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(lot.tank, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (lot.closed) {
                                    StatusPill("CERRADO", AppTheme.status.info)
                                } else if (lot.id == state.currentLotId) {
                                    StatusPill("ACTIVO", AppTheme.status.good)
                                }
                            }
                            SalePill(lot.sale)
                            Text(
                                "${dateFormat.format(lot.date)} · creado por ${lot.createdByName.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lot.note.isNotBlank()) {
                                Text(lot.note, style = MaterialTheme.typography.bodySmall)
                            }
                            saleDetailText(lot, dateFormat)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (lot.closed && lot.stateByName.isNotBlank()) {
                                Text(
                                    "Cerrado por ${lot.stateByName}" + (lot.stateAt?.let { " · ${dateFormat.format(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { releasing = lot },
                                modifier = Modifier.padding(top = 4.dp)
                            ) { Text(if (lot.sale == null) "Liberar para venta" else "Cambiar decisión de venta") }
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Editar lote")
                    }
                }
            }
        }
    }

    editing?.let { lot ->
        var tank by rememberSaveable(lot.id) { mutableStateOf(lot.tank) }
        var note by rememberSaveable(lot.id) { mutableStateOf(lot.note) }
        val tankError = MeasurementValidator.validateTankName(tank)
        AlertDialog(
            onDismissRequest = { if (!state.saving) editing = null },
            title = { Text("Editar lote") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ID: ${lot.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tank,
                        onValueChange = { if (it.length <= 40) tank = it },
                        label = { Text("Estanque") },
                        singleLine = true,
                        enabled = !state.saving,
                        isError = tankError != null,
                        supportingText = if (tankError != null) {
                            { Text(tankError) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 200) note = it },
                        label = { Text("Observación") },
                        placeholder = { Text("Ej.: retirado por el camión a las 18:00") },
                        enabled = !state.saving,
                        supportingText = { Text("${note.length}/200") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "El ID y la fecha del lote no cambian: las mediciones ya registradas siguen asociadas a él.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { togglingClosed = lot },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (lot.closed) "Reabrir lote" else "Cerrar lote") }
                    if (canDelete) {
                        OutlinedButton(
                            onClick = { deleting = lot },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Eliminar lote…", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateLot(lot, tank, note) { editing = null } },
                    enabled = !state.saving && tankError == null
                ) { Text(if (state.saving) "Guardando…" else "Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }, enabled = !state.saving) { Text("Cancelar") }
            }
        )
    }

    releasing?.let { lot ->
        val analysis = analyses[lot.id]
        SaleDialog(
            lot = lot,
            analysis = analysis,
            measurementCount = analysis?.readings ?: 0,
            simulatedData = lot.id in simulatedLotIds,
            saving = state.saving,
            pendingAlerts = state.pendingAlerts.count { it.lotId == lot.id },
            pendingReviews = state.pendingReviews.count { it.lotId == lot.id },
            onConfirm = { decision, note ->
                viewModel.setLotSale(
                    lot = lot,
                    decision = decision,
                    note = note,
                    verdict = analysis?.analysis?.verdict()?.level,
                    measurementCount = analysis?.readings ?: 0,
                    overdue = analysis?.overdue == true
                ) { releasing = null }
            },
            onDismiss = { releasing = null }
        )
    }

    deleting?.let { lot ->
        ConfirmDialog(
            title = "¿Eliminar el lote ${lot.tank}?",
            text = "Se eliminan el lote, TODAS sus mediciones y sus alertas. No se puede deshacer, y los reportes " +
                "futuros ya no las incluirán. Queda anotado en la bitácora. Si solo quieres que no se mida más, usa " +
                "Cerrar lote.",
            confirmLabel = "Eliminar todo",
            destructive = true,
            onConfirm = {
                viewModel.deleteLot(lot) {
                    deleting = null
                    editing = null
                }
            },
            onDismiss = { deleting = null }
        )
    }

    togglingClosed?.let { lot ->
        ConfirmDialog(
            title = if (lot.closed) "¿Reabrir el lote?" else "¿Cerrar el lote?",
            text = if (lot.closed) {
                "Se podrán registrar mediciones nuevas en ${lot.tank}."
            } else {
                "Ya no se podrán registrar mediciones en ${lot.tank} de este día. " +
                    "Las mediciones guardadas se conservan y el lote se puede reabrir."
            },
            confirmLabel = if (lot.closed) "Reabrir" else "Cerrar lote",
            onConfirm = {
                viewModel.setLotClosed(lot, !lot.closed) {
                    togglingClosed = null
                    editing = null
                }
            },
            onDismiss = { togglingClosed = null }
        )
    }
}
