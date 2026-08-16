package com.kvk.leh.seabuckthorn.data.export

import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import com.kvk.leh.seabuckthorn.util.DateUtils
import java.io.File
import java.io.FileWriter

/** Writes survey GPS points as a GeoJSON FeatureCollection for GIS analysis (QGIS, ArcGIS, etc.). */
object GeoJsonExporter {
    fun export(surveys: List<SurveyEntity>, outputFile: File) {
        val features = surveys.filter { it.latitude != null && it.longitude != null }.joinToString(",\n") { s ->
            """{
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [${s.longitude}, ${s.latitude}${s.altitude?.let { ", $it" } ?: ""}] },
              "properties": {
                "survey_id": "${escape(s.id)}",
                "survey_code": "${escape(s.surveyCode)}",
                "village": "${escape(s.village)}",
                "site_name": "${escape(s.siteName)}",
                "surveyor_name": "${escape(s.surveyorName)}",
                "survey_date": "${DateUtils.epochDayToDisplay(s.surveyDateEpochDay)}",
                "gps_accuracy_m": ${s.gpsAccuracyM ?: "null"},
                "altitude_m": ${s.altitude ?: "null"}
              }
            }""".trimIndent()
        }
        val geojson = """{
  "type": "FeatureCollection",
  "features": [
$features
  ]
}"""
        FileWriter(outputFile).use { it.write(geojson) }
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
}
