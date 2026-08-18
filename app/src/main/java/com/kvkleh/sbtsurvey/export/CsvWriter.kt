package com.kvkleh.sbtsurvey.export

import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import java.io.File
import java.io.OutputStream

/** RFC 4180 CSV writer for survey records. */
object CsvWriter {

    /** UTF-8 BOM so Excel on Windows opens Ladakhi/Bodhi place names correctly. */
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun write(surveys: List<SurveyEntity>, target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().use { write(surveys, it) }
    }

    fun write(surveys: List<SurveyEntity>, out: OutputStream) {
        out.write(BOM)
        val builder = StringBuilder()
        builder.append(SurveyColumns.headers.joinToString(",") { escape(it) }).append("\r\n")
        surveys.forEach { survey ->
            builder.append(
                SurveyColumns.row(survey).joinToString(",") { cell ->
                    when (cell) {
                        is Cell.Text -> escape(cell.value)
                        is Cell.Number -> formatNumber(cell.value)
                        Cell.Blank -> ""
                    }
                }
            ).append("\r\n")
        }
        out.write(builder.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    /** Plain, locale-independent decimal text — never scientific notation. */
    private fun formatNumber(value: Double): String {
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return java.math.BigDecimal(value)
            .setScale(6, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
