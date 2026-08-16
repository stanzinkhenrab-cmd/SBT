package com.kvkleh.sbtsurvey.data.export

import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import java.io.OutputStream

/** Writes the survey table as RFC 4180 CSV. */
object CsvWriter {

    /** Byte-order mark so Excel opens `°Brix` correctly on a Windows machine. */
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun write(surveys: List<SurveyEntity>, out: OutputStream) {
        out.write(UTF8_BOM)
        val text = buildString {
            append(SurveyExportRow.headers.joinToString(",") { escape(it) })
            append("\r\n")
            surveys.forEach { survey ->
                append(SurveyExportRow.values(survey).joinToString(",") { escape(it) })
                append("\r\n")
            }
        }
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
