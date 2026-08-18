package com.kvkleh.sbtsurvey.export

import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML (.xlsx) writer.
 *
 * Writing the SpreadsheetML package by hand avoids pulling Apache POI (tens of MB, and
 * heavy on a field tablet) while producing a file that Excel, LibreOffice and Google
 * Sheets all open natively. Numbers are written as real numeric cells so the sheet can be
 * analysed without reformatting.
 */
object XlsxWriter {

    private const val SHEET_NAME = "Surveys"

    fun write(surveys: List<SurveyEntity>, target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.put("[Content_Types].xml", contentTypes())
            zip.put("_rels/.rels", rootRels())
            zip.put("xl/workbook.xml", workbook())
            zip.put("xl/_rels/workbook.xml.rels", workbookRels())
            zip.put("xl/styles.xml", styles())
            zip.put("xl/worksheets/sheet1.xml", sheet(surveys))
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent().replace("\n", "")

    private fun rootRels(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent().replace("\n", "")

    private fun workbook(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets><sheet name="$SHEET_NAME" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent().replace("\n", "")

    private fun workbookRels(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent().replace("\n", "")

    private fun styles(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
        <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1B6B3A"/><bgColor indexed="64"/></patternFill></fill></fills>
        <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
        <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
        <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>
        <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
        </styleSheet>
    """.trimIndent().replace("\n", "")

    private fun sheet(surveys: List<SurveyEntity>): String {
        val builder = StringBuilder(1024 + surveys.size * 512)
        builder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        builder.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        builder.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        builder.append("<cols>")
        SurveyColumns.headers.forEachIndexed { index, header ->
            val width = (header.length + 4).coerceIn(12, 28)
            builder.append("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""")
        }
        builder.append("</cols><sheetData>")

        builder.append("""<row r="1">""")
        SurveyColumns.headers.forEachIndexed { index, header ->
            builder.append(inlineCell(columnName(index) + "1", header, styleIndex = 1))
        }
        builder.append("</row>")

        surveys.forEachIndexed { rowIndex, survey ->
            val rowNumber = rowIndex + 2
            builder.append("""<row r="$rowNumber">""")
            SurveyColumns.row(survey).forEachIndexed { columnIndex, cell ->
                val ref = columnName(columnIndex) + rowNumber
                when (cell) {
                    is Cell.Text -> builder.append(inlineCell(ref, cell.value, styleIndex = 0))
                    is Cell.Number -> builder.append("""<c r="$ref"><v>${plain(cell.value)}</v></c>""")
                    Cell.Blank -> Unit
                }
            }
            builder.append("</row>")
        }

        builder.append("</sheetData></worksheet>")
        return builder.toString()
    }

    private fun inlineCell(ref: String, value: String, styleIndex: Int): String =
        """<c r="$ref" s="$styleIndex" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>"""

    private fun plain(value: Double): String =
        java.math.BigDecimal(value)
            .setScale(6, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    /** A1, ..., Z1, AA1, AB1, ... */
    internal fun columnName(zeroBasedIndex: Int): String {
        var index = zeroBasedIndex
        val name = StringBuilder()
        while (index >= 0) {
            name.insert(0, ('A' + (index % 26)))
            index = index / 26 - 1
        }
        return name.toString()
    }

    private fun escapeXml(value: String): String {
        val builder = StringBuilder(value.length + 16)
        value.forEach { char ->
            when {
                char == '&' -> builder.append("&amp;")
                char == '<' -> builder.append("&lt;")
                char == '>' -> builder.append("&gt;")
                char == '"' -> builder.append("&quot;")
                char == '\'' -> builder.append("&apos;")
                // Control characters are illegal in XML 1.0 and would corrupt the file.
                char.code < 0x20 && char != '\t' && char != '\n' && char != '\r' -> Unit
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }
}
