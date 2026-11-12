package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.viewmodel.RecordsViewModel

/** Rangos de calidad (solo administrador). Cambian el semaforo, las alertas y la evaluacion de toda la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangesScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val current = state.ranges

    var tempMax by rememberSaveable(current) { mutableStateOf(current.tempMax.format()) }
    var phMin by rememberSaveable(current) { mutableStateOf(current.phMin.format()) }
    var phMax by rememberSaveable(current) { mutableStateOf(current.phMax.format()) }
    var condMin by rememberSaveable(current) { mutableStateOf(current.condMin.format()) }
    var condMax by rememberSaveable(current) { mutableStateOf(current.condMax.format()) }
    var turbMin by rememberSaveable(current) { mutableStateOf(current.turbMin.format()) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    fun save() {
        val values = listOf(tempMax, phMin, phMax, condMin, condMax, turbMin).map { it.toNumberOrNull() }
        if (values.any { it == null }) {
            viewModel.showMessage("Revisa los valores: usa solo números (por ejemplo 6,6 o 6.6).")
            return
        }
        val (t, p1, p2, c1, c2, tb) = values.map { it!! }
        viewModel.saveRanges(QualityRanges(t, p1, p2, c1, c2, tb)) {}
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Rangos de calidad", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Estos valores definen el semáforo, las alertas y la evaluación de calidad en todos los teléfonos. " +
                    "Ajústenlos con el encargado del fundo y después de calibrar la caja.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RangeField("Temperatura máxima (°C)", tempMax, !state.saving) { tempMax = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeField("pH mínimo", phMin, !state.saving, Modifier.weight(1f)) { phMin = it }
                RangeField("pH máximo", phMax, !state.saving, Modifier.weight(1f)) { phMax = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeField("Conductividad mín. (mS/cm)", condMin, !state.saving, Modifier.weight(1f)) { condMin = it }
                RangeField("Conductividad máx. (mS/cm)", condMax, !state.saving, Modifier.weight(1f)) { condMax = it }
            }
            RangeField("Turbidez mínima (%)", turbMin, !state.saving) { turbMin = it }

            Button(
                onClick = { save() },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) { Text(if (state.saving) "Guardando…" else "Guardar rangos") }

            OutlinedButton(
                onClick = {
                    val d = QualityRanges()
                    tempMax = d.tempMax.format()
                    phMin = d.phMin.format()
                    phMax = d.phMax.format()
                    condMin = d.condMin.format()
                    condMax = d.condMax.format()
                    turbMin = d.turbMin.format()
                },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Volver a los valores de referencia") }

            Text(
                "Valores de referencia: temperatura ≤ 4 °C; pH 6,6–6,8; conductividad 4,0–5,5 mS/cm; turbidez ≥ 50 %. " +
                    "\"Volver a los valores de referencia\" solo llena el formulario: hay que pulsar Guardar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RangeField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = value.toNumberOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

private fun Float.format(): String =
    if (this == this.toInt().toFloat()) this.toInt().toString() else this.toString().replace('.', ',')

private fun String.toNumberOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()

private operator fun <T> List<T>.component6(): T = this[5]
