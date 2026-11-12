package com.example.myapplication.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Lot
import com.example.myapplication.model.UserSession
import com.example.myapplication.viewmodel.ExportFormat
import com.example.myapplication.viewmodel.ReportPeriod
import com.example.myapplication.viewmodel.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Exportar el historial en PDF, Excel, Word o CSV y compartirlo (correo, WhatsApp, Drive, etc.). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    session: UserSession,
    lots: List<Lot>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var lotMenu by remember { mutableStateOf(false) }
    val day = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    // Cuando los archivos estan listos se abre el menu "Compartir" de Android.
    LaunchedEffect(state.ready) {
        val report = state.ready ?: return@LaunchedEffect
        if (report.uris.isEmpty()) return@LaunchedEffect
        val send = if (report.uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, report.uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(report.uris))
            }
        }.apply {
            type = report.mimeType
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de calidad de la leche")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir reporte"))
        viewModel.shareStarted()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reportes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Genera un reporte de las mediciones guardadas y compártelo por correo, WhatsApp o Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Periodo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                        label = { Text(p.label) }
                    )
                }
            }

            Text("Lote", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(onClick = { lotMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        lots.firstOrNull { it.id == state.lotId }?.let { "${it.tank} · ${day.format(it.date)}" }
                            ?: "Todos los lotes",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = lotMenu, onDismissRequest = { lotMenu = false }) {
                    DropdownMenuItem(text = { Text("Todos los lotes") }, onClick = {
                        lotMenu = false
                        viewModel.setLot(null)
                    })
                    lots.forEach { lot ->
                        DropdownMenuItem(text = { Text("${lot.tank} · ${day.format(lot.date)}") }, onClick = {
                            lotMenu = false
                            viewModel.setLot(lot.id)
                        })
                    }
                }
            }

            Text("Formato", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(
                        selected = f in state.formats,
                        onClick = { viewModel.toggleFormat(f) },
                        label = { Text(f.label) }
                    )
                }
                AssistChip(onClick = { viewModel.selectAllFormats() }, label = { Text("Todos") })
            }
            Text(
                "PDF para imprimir o enviar · Excel y CSV para analizar · Word para editar o agregar al informe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { viewModel.generate(session, lots) },
                enabled = !state.generating && state.formats.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (state.generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generar y compartir")
                }
            }
        }
    }
}
