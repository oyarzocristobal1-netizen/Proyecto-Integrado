package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.Tank
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.StatusPill
import com.example.myapplication.ui.components.TextInputDialog
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.RecordsViewModel

private const val TANK_NAME_MAX = 40

/**
 * Catalogo de estanques (encargado y administrador): crear, renombrar y activar o desactivar.
 * No se borran: los lotes antiguos siguen mostrando el nombre con el que se crearon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanksScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Tank?>(null) }
    var deactivating by remember { mutableStateOf<Tank?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Estanques", "${state.activeTanks.size} activo(s)") },
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
                    "Los estanques activos se ofrecen al elegir el lote de hoy. " +
                        "Un estanque no se borra: se desactiva y deja de ofrecerse.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Button(
                    onClick = { creating = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nuevo estanque")
                }
            }
            if (state.tanks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Add,
                        title = "Sin estanques",
                        message = "Crea los estanques del fundo para elegirlos al medir. " +
                            "Mientras no haya ninguno, el nombre se escribe a mano.",
                        actionLabel = "Nuevo estanque",
                        onAction = { creating = true }
                    )
                }
            }
            items(state.tanks, key = { it.id }) { tank ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tank.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (tank.active) {
                                StatusPill("ACTIVO", AppTheme.status.good)
                            } else {
                                StatusPill("DESACTIVADO", AppTheme.status.warning)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { renaming = tank }, enabled = !state.saving) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Renombrar")
                            }
                            if (tank.active) {
                                TextButton(onClick = { deactivating = tank }, enabled = !state.saving) {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desactivar", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                TextButton(onClick = { viewModel.setTankActive(tank, true) }, enabled = !state.saving) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Activar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Nuevo estanque",
            label = "Nombre",
            placeholder = "Ej.: Estanque 1",
            initial = "",
            maxLength = TANK_NAME_MAX,
            confirmLabel = "Crear",
            saving = state.saving,
            singleLine = true,
            validate = MeasurementValidator::validateTankName,
            stateKey = "nuevo-estanque",
            onConfirm = { name -> viewModel.createTank(name) { creating = false } },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { tank ->
        TextInputDialog(
            title = "Renombrar estanque",
            description = "Los lotes ya creados conservan el nombre con el que se crearon.",
            label = "Nombre",
            initial = tank.name,
            maxLength = TANK_NAME_MAX,
            confirmLabel = "Guardar",
            saving = state.saving,
            singleLine = true,
            validate = MeasurementValidator::validateTankName,
            stateKey = tank.id,
            onConfirm = { name -> viewModel.renameTank(tank, name) { renaming = null } },
            onDismiss = { renaming = null }
        )
    }

    deactivating?.let { tank ->
        ConfirmDialog(
            title = "¿Desactivar ${tank.name}?",
            text = "Dejará de ofrecerse al elegir el lote. Sus lotes y mediciones se conservan " +
                "y puedes activarlo de nuevo cuando quieras.",
            confirmLabel = "Desactivar",
            onConfirm = {
                viewModel.setTankActive(tank, false)
                deactivating = null
            },
            onDismiss = { deactivating = null }
        )
    }
}
