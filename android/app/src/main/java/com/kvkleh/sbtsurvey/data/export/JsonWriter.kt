package com.kvkleh.sbtsurvey.data.export

import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/** Writes the survey table as a JSON document with a small metadata header. */
object JsonWriter {

    fun write(surveys: List<SurveyEntity>, appVersion: String, out: OutputStream) {
        val records = JSONArray()
        surveys.forEach { survey ->
            val values = SurveyExportRow.values(survey)
            val record = JSONObject()
            SurveyExportRow.jsonKeys.forEachIndexed { index, key ->
                val raw = values[index]
                if (index in SurveyExportRow.numericColumns) {
                    val number = raw.toDoubleOrNull()
                    if (number == null) record.put(key, JSONObject.NULL) else record.put(key, number)
                } else {
                    record.put(key, raw)
                }
            }
            records.put(record)
        }

        val root = JSONObject()
            .put("dataset", "Seabuckthorn Field Survey – Ladakh")
            .put("app_version", appVersion)
            .put("exported_at", Formats.dateTime(System.currentTimeMillis()))
            .put("record_count", surveys.size)
            .put("records", records)

        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
    }
}
