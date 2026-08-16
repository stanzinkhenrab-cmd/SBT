package com.kvkleh.sbtsurvey.data.export

import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a real `.xlsx` workbook.
 *
 * An OOXML workbook is a ZIP of a handful of XML parts, so the file is produced
 * directly rather than pulling a desktop spreadsheet library into the APK: the
 * export stays a few kilobytes, needs no native code and cannot fail on a device
 * with little memory. Numbers are written as numeric cells and the header row is
 * bold and frozen.
 */
object XlsxWriter {

    private const val SHEET_NAME = "Seabuckthorn Survey"

    fun write(surveys: List<SurveyEntity>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(ROOT_RELS.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(workbook().toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(WORKBOOK_RELS.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        zip.write(STYLES.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zip.write(sheet(surveys).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.finish()
        zip.flush()
    }

    private fun workbook(): String = XML_DECL +
        "<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\">" +
        "<sheets><sheet name=\"${escape(SHEET_NAME)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
        "</workbook>"

    private fun sheet(surveys: List<SurveyEntity>): String {
        val columnCount = SurveyExportRow.headers.size
        val lastColumn = columnName(columnCount - 1)
        val rowCount = surveys.size + 1

        val builder = StringBuilder(1024 + surveys.size * 512)
        builder.append(XML_DECL)
        builder.append("<worksheet xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\">")
        builder.append("<dimension ref=\"A1:$lastColumn$rowCount\"/>")
        builder.append(
            "<sheetViews><sheetView workbookViewId=\"0\">" +
                "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
                "</sheetView></sheetViews>"
        )
        builder.append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        builder.append("<cols>")
        SurveyExportRow.headers.forEachIndexed { index, header ->
            val width = (header.length + 6).coerceIn(12, 32)
            builder.append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        builder.append("</cols>")

        builder.append("<sheetData>")
        builder.append("<row r=\"1\">")
        SurveyExportRow.headers.forEachIndexed { index, header ->
            builder.append(inlineCell(columnName(index), 1, header, styleIndex = 1))
        }
        builder.append("</row>")

        surveys.forEachIndexed { rowIndex, survey ->
            val rowNumber = rowIndex + 2
            builder.append("<row r=\"$rowNumber\">")
            SurveyExportRow.values(survey).forEachIndexed { columnIndex, value ->
                if (value.isEmpty()) return@forEachIndexed
                val reference = columnName(columnIndex)
                val numeric = columnIndex in SurveyExportRow.numericColumns &&
                    value.toDoubleOrNull() != null
                if (numeric) {
                    builder.append("<c r=\"$reference$rowNumber\"><v>$value</v></c>")
                } else {
                    builder.append(inlineCell(reference, rowNumber, value, styleIndex = 0))
                }
            }
            builder.append("</row>")
        }
        builder.append("</sheetData>")
        builder.append("<autoFilter ref=\"A1:$lastColumn$rowCount\"/>")
        builder.append("</worksheet>")
        return builder.toString()
    }

    private fun inlineCell(column: String, row: Int, value: String, styleIndex: Int): String {
        val style = if (styleIndex == 0) "" else " s=\"$styleIndex\""
        return "<c r=\"$column$row\"$style t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            escape(value) + "</t></is></c>"
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    fun columnName(index: Int): String {
        var remaining = index
        val name = StringBuilder()
        while (remaining >= 0) {
            name.insert(0, ('A' + remaining % 26))
            remaining = remaining / 26 - 1
        }
        return name.toString()
    }

    private fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else ->
                    // Strip control characters that OOXML does not allow.
                    if (character.code >= 0x20 || character == '\t' || character == '\n') {
                        append(character)
                    }
            }
        }
    }

    private const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    private val CONTENT_TYPES = XML_DECL +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private val ROOT_RELS = XML_DECL +
        "<Relationships xmlns=\"$NS_PKG_REL\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private val WORKBOOK_RELS = XML_DECL +
        "<Relationships xmlns=\"$NS_PKG_REL\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private val STYLES = XML_DECL +
        "<styleSheet xmlns=\"$NS_MAIN\">" +
        "<fonts count=\"2\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        "<fills count=\"2\">" +
        "<fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "</fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"2\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
