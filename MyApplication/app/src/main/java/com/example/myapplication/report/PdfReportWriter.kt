package com.example.myapplication.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** PDF con el generador de Android (sin librerias): titulo, resumen y tabla con paginas A4. */
object PdfReportWriter {
    private const val PAGE_WIDTH = 595 // A4 en puntos
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 16f

    // Anchos de columna en puntos (suman 515 = ancho util)
    private val widths = floatArrayOf(78f, 70f, 92f, 50f, 36f, 64f, 50f, 75f)
    private val headers = listOf("Fecha y hora", "Lote", "Lechero", "Temp. °C", "pH", "CE mS/cm", "Turb. %", "Estado")

    fun write(data: ReportData, out: OutputStream) {
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val text = Paint().apply { textSize = 9.5f; isAntiAlias = true }
        val headerText = Paint().apply { textSize = 8f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val cellText = Paint().apply { textSize = 8f; isAntiAlias = true }
        val headerFill = Paint().apply { color = Color.rgb(0xDC, 0xE9, 0xF5) }
        val line = Paint().apply { color = Color.rgb(0xA6, 0xB8, 0xCC); strokeWidth = 0.5f }
        val footer = Paint().apply { textSize = 8f; color = Color.GRAY; isAntiAlias = true }

        var pageNumber = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun drawTableHeader() {
            var x = MARGIN
            page.canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, headerFill)
            headers.forEachIndexed { i, h ->
                page.canvas.drawText(fit(h, headerText, widths[i] - 4f), x + 2f, y + 11f, headerText)
                x += widths[i]
            }
            y += ROW_HEIGHT
            page.canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        }

        fun newPage(withTableHeader: Boolean) {
            if (pageNumber > 0) {
                page.canvas.drawText(
                    "Página $pageNumber · ${data.title}", MARGIN, PAGE_HEIGHT - 20f, footer
                )
                doc.finishPage(page)
            }
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            y = MARGIN
            if (withTableHeader) drawTableHeader()
        }

        newPage(withTableHeader = false)
        page.canvas.drawText(data.title, MARGIN, y + 14f, title)
        y += 30f
        data.summaryLines().forEach { s ->
            page.canvas.drawText(fit(s, text, PAGE_WIDTH - 2 * MARGIN), MARGIN, y, text)
            y += 14f
        }
        y += 8f
        drawTableHeader()

        if (data.rows.isEmpty()) {
            page.canvas.drawText("No hay mediciones en el periodo elegido.", MARGIN, y + 14f, text)
        }
        data.rows.forEach { row ->
            if (y + ROW_HEIGHT > PAGE_HEIGHT - 40f) newPage(withTableHeader = true)
            var x = MARGIN
            ReportFormat.cells(row).forEachIndexed { i, value ->
                page.canvas.drawText(fit(value, cellText, widths[i] - 4f), x + 2f, y + 11f, cellText)
                x += widths[i]
            }
            y += ROW_HEIGHT
            page.canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        }

        page.canvas.drawText("Página $pageNumber · ${data.title}", MARGIN, PAGE_HEIGHT - 20f, footer)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /** Recorta el texto con "…" si no cabe en el ancho de la columna. */
    private fun fit(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end--
        return value.substring(0, end) + "…"
    }
}
