package com.kvk.leh.seabuckthorn.data.export

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal, dependency-free writer for multi-sheet .xlsx (OOXML spreadsheet) files.
 *
 * Rather than pulling in a heavyweight library (Apache POI is built for desktop JVMs and drags
 * in AWT/java.beans, which is awkward on Android), this writes the handful of XML parts a
 * spreadsheet app needs directly, using inline strings so no shared-strings table is required.
 * It supports exactly what this app needs: named sheets of String/Number/null cells.
 */
class XlsxWriter {
    private val sheetNames = mutableListOf<String>()
    private val sheetRows = mutableListOf<List<List<Any?>>>()

    /** Adds a sheet. [header] becomes row 1; [rows] follow. Cell values must be String, Number, or null. */
    fun addSheet(name: String, header: List<String>, rows: List<List<Any?>>) {
        val safeName = sanitizeSheetName(name)
        sheetNames.add(safeName)
        val allRows = ArrayList<List<Any?>>(rows.size + 1)
        allRows.add(header)
        allRows.addAll(rows)
        sheetRows.add(allRows)
    }

    fun writeTo(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypesXml())
            entry(zip, "_rels/.rels", rootRelsXml())
            entry(zip, "xl/workbook.xml", workbookXml())
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            sheetRows.forEachIndexed { index, rows ->
                entry(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(rows))
            }
        }
    }

    private fun entry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/*?:\\[\\]]"), " ").trim()
        return cleaned.take(31).ifBlank { "Sheet" }
    }

    private fun contentTypesXml(): String {
        val overrides = sheetRows.indices.joinToString("") { i ->
            "<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overrides
</Types>"""
    }

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(): String {
        val sheetsXml = sheetNames.mapIndexed { i, name ->
            "<sheet name=\"${xmlEscape(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$sheetsXml</sheets>
</workbook>"""
    }

    private fun workbookRelsXml(): String {
        val rels = sheetRows.indices.joinToString("") { i ->
            "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
</Relationships>"""
    }

    private fun sheetXml(rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, row ->
            val rowNum = rowIndex + 1
            sb.append("<row r=\"$rowNum\">")
            row.forEachIndexed { colIndex, value ->
                if (value != null) {
                    val ref = columnLetters(colIndex) + rowNum
                    when (value) {
                        is Number -> sb.append("<c r=\"$ref\"><v>${value}</v></c>")
                        else -> sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(value.toString())}</t></is></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun columnLetters(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    private fun xmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
