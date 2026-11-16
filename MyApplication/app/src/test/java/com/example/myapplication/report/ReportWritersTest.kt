package com.example.myapplication.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ReportWritersTest {

    private val now = Date(1_794_600_000_000L)

    private fun data(rows: List<ReportRow>) = ReportData(
        title = "Reporte de prueba",
        period = "Hoy",
        lotFilter = "Todos los lotes",
        generatedBy = "Prueba (Administrador)",
        generatedAt = now,
        rows = rows
    )

    private val simulada = ReportRow(now, "Estanque 1", "Lechero A", 3.8, 6.7, 4.8, 85.0, "verde", simulated = true)
    private val real = ReportRow(now, "Estanque; Norte", "Lechero B", 22.0, 7.15, 6.8, 60.0, "rojo", simulated = false)

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return out
    }

    private fun assertWellFormedXml(xml: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }

    @Test
    fun resumenAdvierteDatosSimulados() {
        assertTrue(data(listOf(simulada, real)).summaryLines().any { it.contains("1 mediciones SIMULADAS") })
        assertFalse(data(listOf(real)).summaryLines().any { it.contains("SIMULADAS") })
    }

    @Test
    fun resumenCalculaSemaforoYPromedios() {
        val d = data(listOf(simulada, real))
        assertEquals(2, d.total)
        assertEquals(50.0, d.percent("verde"), 0.001)
        assertEquals(12.9, d.average { it.temperature }!!, 0.001)
    }

    @Test
    fun csvConBomSeparadorPuntoYComaYComaDecimal() {
        val out = ByteArrayOutputStream()
        CsvReportWriter.write(data(listOf(simulada, real)), out)
        val text = out.toByteArray().toString(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿"))
        val lines = text.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith(";Estado;Origen"))
        assertTrue(lines[1].contains(";6,70;"))
        assertTrue(lines[1].endsWith(";Normal;Simulada"))
        // Un ";" dentro del nombre del lote va entre comillas
        assertTrue(lines[2].contains(";\"Estanque; Norte\";"))
        assertTrue(lines[2].endsWith(";Fuera de rango;Sensor"))
    }

    @Test
    fun csvNeutralizaFormulasEscritasEnLoteOLechero() {
        val ataque = ReportRow(now, "=HYPERLINK(\"http://malo.cl\")", "+cmd|' /C calc'!A0", -1.5, 6.7, 4.8, 85.0, "verde", simulated = false)
        val out = ByteArrayOutputStream()
        CsvReportWriter.write(data(listOf(ataque)), out)
        val line = out.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF").split("\r\n")[1]
        assertTrue(line.contains("'=HYPERLINK"))
        assertTrue(line.contains("'+cmd"))
        // Los numeros negativos (temperatura bajo cero) no se alteran
        assertTrue(line.contains(";-1,5;"))
        assertEquals("Estanque 1", CsvReportWriter.neutralizeFormula("Estanque 1"))
        assertEquals("'@usuario", CsvReportWriter.neutralizeFormula("@usuario"))
    }

    @Test
    fun xlsxEsUnArchivoExcelValido() {
        val out = ByteArrayOutputStream()
        XlsxReportWriter.write(data(listOf(simulada, real)), out)
        val entries = zipEntries(out.toByteArray())
        listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"
        ).forEach { assertTrue("Falta $it", it in entries) }
        entries.values.forEach { assertWellFormedXml(it) }
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains(">Origen<"))
        assertTrue(sheet.contains("<v>6.7</v>"))
        assertTrue(sheet.contains(">Simulada<"))
        assertTrue(sheet.contains("Estanque; Norte"))
    }

    @Test
    fun docxEsUnDocumentoWordValido() {
        val out = ByteArrayOutputStream()
        DocxReportWriter.write(data(listOf(simulada, real)), out)
        val entries = zipEntries(out.toByteArray())
        assertTrue("word/document.xml" in entries)
        entries.values.forEach { assertWellFormedXml(it) }
        val doc = entries.getValue("word/document.xml")
        assertTrue(doc.contains("SIMULADAS"))
        assertTrue(doc.contains(">Sensor<"))
    }

    @Test
    fun reportesSinMedicionesTambienSonValidos() {
        val vacio = data(emptyList())
        val xlsx = ByteArrayOutputStream().also { XlsxReportWriter.write(vacio, it) }
        val docx = ByteArrayOutputStream().also { DocxReportWriter.write(vacio, it) }
        zipEntries(xlsx.toByteArray()).values.forEach { assertWellFormedXml(it) }
        val doc = zipEntries(docx.toByteArray()).getValue("word/document.xml")
        assertWellFormedXml(doc)
        assertTrue(doc.contains("No hay mediciones"))
    }

    @Test
    fun textosConCaracteresEspecialesSeEscapan() {
        val raro = simulada.copy(lot = "Lote <A> & \"B\"")
        val out = ByteArrayOutputStream()
        DocxReportWriter.write(data(listOf(raro)), out)
        assertWellFormedXml(zipEntries(out.toByteArray()).getValue("word/document.xml"))
    }
}
