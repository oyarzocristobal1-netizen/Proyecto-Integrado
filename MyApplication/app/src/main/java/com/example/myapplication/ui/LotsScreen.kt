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
import com.example.myapplication.viewmodel.RecordsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Lotes recientes; el encargado y el administrador pueden editar el estanque y la observacion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Lot?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Lotes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    "Toca un lote para corregir el estanque o agregar una observación. Los lotes no se borran.",
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
                            Text(lot.tank, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${dateFormat.format(lot.date)} · creado por ${lot.createdByName.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lot.note.isNotBlank()) {
                                Text(lot.note, style = MaterialTheme.typography.bodySmall)
                            }
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
                        onValueChange = { tank = it },
                        label = { Text("Estanque") },
                        singleLine = true,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Observación") },
                        placeholder = { Text("Ej.: retirado por el camión a las 18:00") },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateLot(lot, tank, note) { editing = null } },
                    enabled = !state.saving
                ) { Text(if (state.saving) "Guardando…" else "Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }, enabled = !state.saving) { Text("Cancelar") }
            }
        )
    }
}
