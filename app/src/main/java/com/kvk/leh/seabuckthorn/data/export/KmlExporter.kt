package com.kvk.leh.seabuckthorn.data.export

import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import com.kvk.leh.seabuckthorn.util.DateUtils
import java.io.File
import java.io.FileWriter

/** Writes survey GPS points as a KML placemark file for Google Earth / GIS applications. */
object KmlExporter {
    fun export(surveys: List<SurveyEntity>, outputFile: File) {
        val placemarks = surveys.filter { it.latitude != null && it.longitude != null }.joinToString("\n") { s ->
            val altitude = s.altitude ?: 0.0
            """
            <Placemark>
              <name>${escape(s.surveyCode)}</name>
              <description>${escape("Village: ${s.village}\nSurveyor: ${s.surveyorName}\nDate: ${DateUtils.epochDayToDisplay(s.surveyDateEpochDay)}\nAccuracy: ${s.gpsAccuracyM?.let { "±%.1f m".format(it) } ?: "unknown"}")}</description>
              <Point>
                <coordinates>${s.longitude},${s.latitude},$altitude</coordinates>
              </Point>
            </Placemark>
            """.trimIndent()
        }
        val kml = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
<Document>
<name>Seabuckthorn Field Survey - Ladakh</name>
$placemarks
</Document>
</kml>"""
        FileWriter(outputFile).use { it.write(kml) }
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
