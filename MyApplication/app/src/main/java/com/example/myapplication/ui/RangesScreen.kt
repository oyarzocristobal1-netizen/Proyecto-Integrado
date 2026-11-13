package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.QualityRanges
import com.example.myapplication.ui.components.InfoBanner
import com.example.myapplication.ui.components.PrimaryPillButton
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.components.SecondaryPillButton
import com.example.myapplication.ui.components.SectionCard
import com.example.myapplication.ui.components.WarningNote
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
    val reference = remember { QualityRanges() }

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
        val v = values.map { it!! }
        viewModel.saveRanges(QualityRanges(v[0], v[1], v[2], v[3], v[4], v[5])) {}
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Rangos de calidad") },
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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            InfoBanner(
                "Estos valores definen el semáforo, las alertas y la evaluación de calidad en todos los teléfonos. " +
                    "Ajústenlos con el encargado del fundo y después de calibrar la caja."
            )

            SectionCard("🌡️", "Temperatura", "Referencia: ≤ ${reference.tempMax.format()} °C") {
                RangeField("Temperatura máxima", tempMax, "°C", !state.saving) { tempMax = it }
            }

            SectionCard("🧪", "pH", "Referencia: ${reference.phMin.format()} – ${reference.phMax.format()}") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RangeField("pH mínimo", phMin, null, !state.saving, Modifier.weight(1f)) { phMin = it }
                    RangeField("pH máximo", phMax, null, !state.saving, Modifier.weight(1f)) { phMax = it }
                }
            }

            SectionCard(
                "⚡",
                "Conductividad (mS/cm)",
                "Referencia: ${reference.condMin.format(1)} – ${reference.condMax.format(1)}"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RangeField("Mínima", condMin, null, !state.saving, Modifier.weight(1f)) { condMin = it }
                    RangeField("Máxima", condMax, null, !state.saving, Modifier.weight(1f)) { condMax = it }
                }
            }

            SectionCard("🌀", "Turbidez (%)", "Referencia: ≥ ${reference.turbMin.format()} %") {
                RangeField("Turbidez mínima", turbMin, "%", !state.saving) { turbMin = it }
            }

            Spacer(modifier = Modifier.height(2.dp))

            PrimaryPillButton(
                text = if (state.saving) "Guardando…" else "Guardar rangos",
                leadingIcon = Icons.Default.Save,
                trailingIcon = Icons.Default.CheckCircle,
                loading = state.saving,
                onClick = { save() }
            )

            SecondaryPillButton(
                text = "Restablecer valores de referencia",
                trailingIcon = Icons.Default.Refresh,
                enabled = !state.saving,
                onClick = {
                    tempMax = reference.tempMax.format()
                    phMin = reference.phMin.format()
                    phMax = reference.phMax.format()
                    condMin = reference.condMin.format()
                    condMax = reference.condMax.format()
                    turbMin = reference.turbMin.format()
                }
            )

            WarningNote(
                "'Restablecer valores de referencia' solo llena el formulario; " +
                    "es necesario presionar 'Guardar rangos' para aplicar los cambios."
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RangeField(
    label: String,
    value: String,
    unit: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = unit?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = value.toNumberOrNull() == null,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** 6.6 -> "6,6"; 4.0 -> "4" (o "4,0" si se piden decimales). */
private fun Float.format(minDecimals: Int = 0): String {
    val whole = this == this.toInt().toFloat()
    return if (whole && minDecimals == 0) {
        this.toInt().toString()
    } else if (whole) {
        "%.${minDecimals}f".format(java.util.Locale.US, this).replace('.', ',')
    } else {
        this.toString().replace('.', ',')
    }
}

private fun String.toNumberOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()
