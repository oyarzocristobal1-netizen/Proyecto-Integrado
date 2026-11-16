package com.example.myapplication.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.Lot
import com.example.myapplication.data.REPORT_LIMIT
import com.example.myapplication.data.RecordsRepository
import com.example.myapplication.data.toUserMessage
import com.example.myapplication.model.UserSession
import com.example.myapplication.report.CsvReportWriter
import com.example.myapplication.report.DocxReportWriter
import com.example.myapplication.report.PdfReportWriter
import com.example.myapplication.report.ReportData
import com.example.myapplication.report.ReportRow
import com.example.myapplication.report.XlsxReportWriter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod(val label: String, val days: Int) {
    TODAY("Hoy", 0),
    WEEK("Últimos 7 días", 7),
    MONTH("Últimos 30 días", 30)
}

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    XLSX("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("Word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    CSV("CSV", "csv", "text/csv")
}

/** Archivos listos para compartir. */
data class GeneratedReport(val uris: List<Uri>, val mimeType: String, val rows: Int)

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.WEEK,
    val lotId: String? = null,
    /** Formatos elegidos (se pueden elegir varios o todos). */
    val formats: Set<ExportFormat> = setOf(ExportFormat.PDF),
    /**
     * Incluir mediciones simuladas (modo DEMO). Por defecto no: un reporte de calidad debe tener
     * solo datos reales. Si se incluyen, cada fila y el resumen quedan marcados como SIMULADOS.
     */
    val includeSimulated: Boolean = false,
    val generating: Boolean = false,
    val message: String? = null,
    /** Se llena cuando hay archivos para compartir; la pantalla abre "Compartir" y lo limpia. */
    val ready: GeneratedReport? = null
)

/** Reportes del historial en PDF, Excel, Word y CSV (solo encargado y administrador). */
class ReportsViewModel(
    private val app: Application,
    private val repository: RecordsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    fun setPeriod(period: ReportPeriod) = _state.update { it.copy(period = period) }

    fun setLot(lotId: String?) = _state.update { it.copy(lotId = lotId) }

    fun toggleFormat(format: ExportFormat) = _state.update {
        val formats = if (format in it.formats) it.formats - format else it.formats + format
        it.copy(formats = formats)
    }

    fun selectAllFormats() = _state.update { it.copy(formats = ExportFormat.entries.toSet()) }

    fun setIncludeSimulated(include: Boolean) = _state.update { it.copy(includeSimulated = include) }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun shareStarted() = _state.update { it.copy(ready = null) }

    fun generate(session: UserSession, lots: List<Lot>) {
        val st = _state.value
        if (!session.role.canExportReports) {
            _state.update { it.copy(message = "Tu rol no permite exportar reportes.") }
            return
        }
        if (st.formats.isEmpty()) {
            _state.update { it.copy(message = "Elige al menos un formato.") }
            return
        }
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                val (from, to) = range(st.period)
                val all = repository.fetchForReport(from, to, st.lotId)
                val measurements = if (st.includeSimulated) all else all.filter { !it.isSimulated }
                if (measurements.isEmpty()) {
                    val simulated = all.count { it.isSimulated }
                    val text = if (simulated > 0 && !st.includeSimulated) {
                        "El periodo solo tiene $simulated medición(es) simulada(s). Activa \"Incluir simuladas\" " +
                            "para exportarlas (quedarán marcadas como SIMULADAS)."
                    } else {
                        "No hay mediciones en el periodo y lote elegidos."
                    }
                    _state.update { it.copy(generating = false, message = text) }
                    return@launch
                }
                val lotNames = lots.associate { it.id to it.tank }
                val day = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                val data = ReportData(
                    title = "Reporte de calidad de la leche - Fundo San Carlos",
                    period = "${st.period.label} (${day.format(from)} a ${day.format(to)})",
                    lotFilter = st.lotId?.let { lotNames[it] ?: it } ?: "Todos los lotes",
                    generatedBy = "${session.name} (${session.role.label})",
                    generatedAt = Date(),
                    rows = measurements.map { m ->
                        ReportRow(
                            dateTime = m.dateTime,
                            lot = lotNames[m.lotId] ?: m.lotId,
                            milker = m.milkerName,
                            temperature = m.temperature,
                            ph = m.ph,
                            conductivity = m.conductivity,
                            turbidity = m.turbidity,
                            status = m.status,
                            simulated = m.isSimulated
                        )
                    }
                )
                val uris = withContext(Dispatchers.IO) { writeFiles(data, st.formats) }
                val mime = if (st.formats.size == 1) st.formats.first().mimeType else "*/*"
                // Al terminar se abre directamente "Compartir"; solo se avisa si el reporte quedo recortado.
                val limitNote = if (all.size >= REPORT_LIMIT) {
                    "Se incluyeron las $REPORT_LIMIT mediciones más recientes (máximo por reporte): acota el periodo."
                } else {
                    null
                }
                _state.update {
                    it.copy(
                        generating = false,
                        ready = GeneratedReport(uris, mime, data.rows.size),
                        message = limitNote
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, message = friendlyMessage(e)) }
            }
        }
    }

    /** Desde el inicio del dia (hoy o hace N dias) hasta ahora. */
    private fun range(period: ReportPeriod): Pair<Date, Date> {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -period.days)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return start.time to Date()
    }

    private fun writeFiles(data: ReportData, formats: Set<ExportFormat>): List<Uri> {
        val dir = File(app.cacheDir, "reportes").apply { mkdirs() }
        // Se borran los reportes anteriores para no llenar la memoria del telefono.
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(data.generatedAt)
        return formats.sortedBy { it.ordinal }.map { format ->
            val file = File(dir, "reporte-calidad-leche-$stamp.${format.extension}")
            file.outputStream().use { out ->
                when (format) {
                    ExportFormat.PDF -> PdfReportWriter.write(data, out)
                    ExportFormat.XLSX -> XlsxReportWriter.write(data, out)
                    ExportFormat.DOCX -> DocxReportWriter.write(data, out)
                    ExportFormat.CSV -> CsvReportWriter.write(data, out)
                }
            }
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        }
    }

    private fun friendlyMessage(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Sin conexión: el reporte necesita internet para leer todas las mediciones del servidor."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permiso denegado: solo el encargado y el administrador pueden exportar."
        e is FirebaseFirestoreException -> e.toUserMessage()
        e is java.io.IOException -> "No se pudo escribir el archivo del reporte en el teléfono."
        else -> e.toUserMessage()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ReportsViewModel(app, appContainer().recordsRepository)
            }
        }
    }
}
