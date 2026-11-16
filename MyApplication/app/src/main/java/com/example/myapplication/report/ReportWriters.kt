package com.example.myapplication.report

import java.io.OutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Una fila del reporte: una medicion. */
data class ReportRow(
    val dateTime: Date,
    val lot: String,
    val milker: String,
    val temperature: Double,
    val ph: Double,
    val conductivity: Double,
    val turbidity: Double,
    /** verde, ambar o rojo */
    val status: String,
    /** true si la medicion se tomo en modo DEMO (datos simulados, no reales) */
    val simulated: Boolean
)

/** Contenido del reporte, independiente del formato. */
data class ReportData(
    val title: String,
    val period: String,
    val lotFilter: String,
    val generatedBy: String,
    val generatedAt: Date,
    val rows: List<ReportRow>
) {
    val total: Int get() = rows.size
    fun count(status: String): Int = rows.count { it.status == status }
    fun percent(status: String): Double = if (rows.isEmpty()) 0.0 else count(status) * 100.0 / rows.size
    fun average(selector: (ReportRow) -> Double): Double? =
        if (rows.isEmpty()) null else rows.sumOf(selector) / rows.size

    /** Resumen en lineas de texto (lo usan PDF y Word). */
    fun summaryLines(): List<String> {
        val lines = mutableListOf(
            "Periodo: $period",
            "Lote: $lotFilter",
            "Generado por: $generatedBy, el ${ReportFormat.dateTime(generatedAt)}",
            "Mediciones: $total"
        )
        val simulatedCount = rows.count { it.simulated }
        if (simulatedCount > 0) {
            lines += "ATENCIÓN: incluye $simulatedCount mediciones SIMULADAS (modo DEMO). No son mediciones reales de leche."
        }
        if (rows.isNotEmpty()) {
            lines += "Semáforo: verde ${count("verde")} (${ReportFormat.decimal(percent("verde"), 1)} %), " +
                "ámbar ${count("ambar")} (${ReportFormat.decimal(percent("ambar"), 1)} %), " +
                "rojo ${count("rojo")} (${ReportFormat.decimal(percent("rojo"), 1)} %)"
            lines += "Promedios: temperatura ${ReportFormat.decimal(average { it.temperature }!!, 1)} °C, " +
                "pH ${ReportFormat.decimal(average { it.ph }!!, 2)}, " +
                "conductividad ${ReportFormat.decimal(average { it.conductivity }!!, 2)} mS/cm, " +
                "turbidez ${ReportFormat.decimal(average { it.turbidity }!!, 1)} %"
        }
        return lines
    }

    companion object {
        val HEADERS = listOf(
            "Fecha y hora", "Lote", "Lechero", "Temperatura (°C)", "pH",
            "Conductividad (mS/cm)", "Turbidez (%)", "Estado", "Origen"
        )
    }
}

/** Formatos de numeros y fechas en espanol de Chile (coma decimal). */
object ReportFormat {
    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-CL")).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    fun decimal(value: Double, digits: Int): String {
        val pattern = if (digits == 0) "0" else "0." + "0".repeat(digits)
        return DecimalFormat(pattern, symbols).format(value)
    }

    fun dateTime(date: Date): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(date)

    fun statusLabel(status: String): String = when (status) {
        "verde" -> "Normal"
        "ambar" -> "Precaución"
        "rojo" -> "Fuera de rango"
        else -> status
    }

    /** Valores de una fila como texto (CSV y Word). */
    fun cells(row: ReportRow): List<String> = listOf(
        dateTime(row.dateTime),
        row.lot,
        row.milker,
        decimal(row.temperature, 1),
        decimal(row.ph, 2),
        decimal(row.conductivity, 2),
        decimal(row.turbidity, 1),
        statusLabel(row.status),
        sourceLabel(row)
    )

    fun sourceLabel(row: ReportRow): String = if (row.simulated) "Simulada" else "Sensor"
}

/** CSV para Excel en Chile: separador punto y coma, coma decimal, UTF-8 con BOM. */
object CsvReportWriter {
    fun write(data: ReportData, out: OutputStream) {
        val sb = StringBuilder()
        sb.append('﻿')
        sb.append(ReportData.HEADERS.joinToString(";") { escape(it) }).append("\r\n")
        data.rows.forEach { row ->
            sb.append(ReportFormat.cells(row).joinToString(";") { escape(it) }).append("\r\n")
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

private fun xml(text: String): String = buildString {
    text.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code >= 0x20 || c == '\t' || c == '\n' || c == '\r') append(c)
        }
    }
}

private fun ZipOutputStream.entry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

/** Excel (.xlsx) minimo: una hoja con encabezado en negrita y numeros como numeros. */
object XlsxReportWriter {
    fun write(data: ReportData, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
                    """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                    """</Types>"""
            )
            zip.entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                    """</Relationships>"""
            )
            zip.entry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
                    """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
                    """<sheets><sheet name="Mediciones" sheetId="1" r:id="rId1"/></sheets></workbook>"""
            )
            zip.entry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
                    """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                    """</Relationships>"""
            )
            zip.entry("xl/styles.xml", STYLES)
            zip.entry("xl/worksheets/sheet1.xml", sheet(data))
        }
    }

    // Estilos de celda: 0 normal, 1 negrita (titulo y encabezado), 2 fecha dd/mm/yyyy hh:mm
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy hh:mm"/></numFmts>""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="3">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """</cellXfs>""" +
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            """</styleSheet>"""

    private fun col(index: Int): String = ('A' + index).toString()

    private fun inlineCell(ref: String, text: String, style: Int = 0): String =
        """<c r="$ref" t="inlineStr"${if (style != 0) " s=\"$style\"" else ""}><is><t xml:space="preserve">${xml(text)}</t></is></c>"""

    private fun numberCell(ref: String, value: Double, style: Int = 0): String =
        """<c r="$ref"${if (style != 0) " s=\"$style\"" else ""}><v>$value</v></c>"""

    /** Redondea para que Excel muestre 6.7 y no 6.69999980926514. */
    private fun round(value: Double, digits: Int): Double =
        java.math.BigDecimal(value).setScale(digits, java.math.RoundingMode.HALF_UP).toDouble()

    /** Fecha de Excel: dias desde 1899-12-30, con la hora local del telefono. */
    private fun excelDate(date: Date): Double {
        val offset = java.util.TimeZone.getDefault().getOffset(date.time)
        return (date.time + offset) / 86_400_000.0 + 25_569.0
    }

    private fun sheet(data: ReportData): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="18" customWidth="1"/><col min="2" max="3" width="22" customWidth="1"/>""")
        sb.append("""<col min="4" max="9" width="16" customWidth="1"/></cols><sheetData>""")
        var r = 1
        sb.append("""<row r="$r">""").append(inlineCell("A$r", data.title, 1)).append("</row>")
        r++
        data.summaryLines().forEach { line ->
            sb.append("""<row r="$r">""").append(inlineCell("A$r", line)).append("</row>")
            r++
        }
        r++
        sb.append("""<row r="$r">""")
        ReportData.HEADERS.forEachIndexed { i, h -> sb.append(inlineCell("${col(i)}$r", h, 1)) }
        sb.append("</row>")
        r++
        data.rows.forEach { row ->
            sb.append("""<row r="$r">""")
            sb.append(numberCell("A$r", excelDate(row.dateTime), 2))
            sb.append(inlineCell("B$r", row.lot))
            sb.append(inlineCell("C$r", row.milker))
            sb.append(numberCell("D$r", round(row.temperature, 1)))
            sb.append(numberCell("E$r", round(row.ph, 2)))
            sb.append(numberCell("F$r", round(row.conductivity, 2)))
            sb.append(numberCell("G$r", round(row.turbidity, 1)))
            sb.append(inlineCell("H$r", ReportFormat.statusLabel(row.status)))
            sb.append(inlineCell("I$r", ReportFormat.sourceLabel(row)))
            sb.append("</row>")
            r++
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }
}

/** Word (.docx) minimo: titulo, resumen y tabla con bordes. */
object DocxReportWriter {
    fun write(data: ReportData, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
                    """</Types>"""
            )
            zip.entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
                    """</Relationships>"""
            )
            zip.entry("word/document.xml", document(data))
        }
    }

    private fun run(text: String, bold: Boolean = false, size: Int = 20): String =
        """<w:r><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>""" +
            (if (bold) "<w:b/>" else "") +
            """<w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr><w:t xml:space="preserve">${xml(text)}</w:t></w:r>"""

    private fun paragraph(text: String, bold: Boolean = false, size: Int = 20, after: Int = 80): String =
        """<w:p><w:pPr><w:spacing w:after="$after"/></w:pPr>${run(text, bold, size)}</w:p>"""

    private val widths = listOf(1300, 1150, 1250, 1100, 650, 1250, 950, 1150, 950) // suma 9750 (A4, margenes 1,8 cm)

    private fun cell(text: String, width: Int, header: Boolean): String =
        """<w:tc><w:tcPr><w:tcW w:w="$width" w:type="dxa"/>""" +
            (if (header) """<w:shd w:val="clear" w:color="auto" w:fill="DCE9F5"/>""" else "") +
            """</w:tcPr><w:p><w:pPr><w:spacing w:after="0"/></w:pPr>${run(text, header, 16)}</w:p></w:tc>"""

    private fun row(cells: List<String>, header: Boolean): String =
        "<w:tr>" + (if (header) "<w:trPr><w:tblHeader/></w:trPr>" else "") +
            cells.mapIndexed { i, c -> cell(c, widths[i], header) }.joinToString("") + "</w:tr>"

    private fun document(data: ReportData): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        sb.append(paragraph(data.title, bold = true, size = 32, after = 160))
        data.summaryLines().forEach { sb.append(paragraph(it)) }
        sb.append(paragraph("", after = 0))
        sb.append("""<w:tbl><w:tblPr><w:tblW w:w="${widths.sum()}" w:type="dxa"/>""")
        sb.append("<w:tblBorders>")
        listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach {
            sb.append("""<w:$it w:val="single" w:sz="4" w:space="0" w:color="A6B8CC"/>""")
        }
        sb.append("</w:tblBorders></w:tblPr><w:tblGrid>")
        widths.forEach { sb.append("""<w:gridCol w:w="$it"/>""") }
        sb.append("</w:tblGrid>")
        sb.append(row(ReportData.HEADERS, header = true))
        data.rows.forEach { sb.append(row(ReportFormat.cells(it), header = false)) }
        sb.append("</w:tbl>")
        if (data.rows.isEmpty()) sb.append(paragraph("No hay mediciones en el periodo elegido."))
        sb.append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""")
        sb.append("""<w:pgMar w:top="1021" w:right="1021" w:bottom="1021" w:left="1021" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
        sb.append("</w:body></w:document>")
        return sb.toString()
    }
}
