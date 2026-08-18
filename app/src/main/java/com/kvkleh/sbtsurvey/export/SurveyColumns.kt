package com.kvkleh.sbtsurvey.export

import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.domain.HeightUnit
import com.kvkleh.sbtsurvey.domain.toMetres
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single exported cell: either free text or a real number. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: Double) : Cell
    data object Blank : Cell
}

private fun text(value: String?): Cell =
    if (value.isNullOrBlank()) Cell.Blank else Cell.Text(value)

private fun number(value: Double?): Cell =
    if (value == null) Cell.Blank else Cell.Number(value)

private fun number(value: Float?): Cell =
    if (value == null) Cell.Blank else Cell.Number(value.toDouble())

private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private fun timestamp(millis: Long?): Cell =
    if (millis == null || millis <= 0) Cell.Blank else Cell.Text(timestampFormat.format(Date(millis)))

/**
 * The exported column set, shared by the CSV and XLSX writers so both files always
 * contain exactly the same fields in the same order.
 */
object SurveyColumns {

    data class Column(val header: String, val value: (SurveyEntity) -> Cell)

    val all: List<Column> = listOf(
        Column("Survey ID") { text(it.surveyId) },
        Column("Surveyor Name") { text(it.surveyorName) },
        Column("Designation") { text(it.designation) },
        Column("Organization") { text(it.organization) },
        Column("Date") { text(it.date) },
        Column("Time") { text(it.time) },
        Column("District") { text(it.district) },
        Column("Block") { text(it.block) },
        Column("Village") { text(it.village) },
        Column("Site") { text(it.site) },
        Column("Latitude") { number(it.latitude) },
        Column("Longitude") { number(it.longitude) },
        Column("Altitude (m)") { number(it.altitude) },
        Column("GPS Accuracy (m)") { number(it.gpsAccuracy) },
        Column("GPS Timestamp") { timestamp(it.gpsTimestamp) },
        Column("Shrub Type") { text(it.shrubType) },
        Column("Plant Height") { number(it.plantHeight) },
        Column("Plant Height Unit") { text(it.plantHeightUnit) },
        Column("Plant Height (m)") { survey ->
            number(
                survey.plantHeight?.toMetres(HeightUnit.fromStorage(survey.plantHeightUnit))
                    ?.let { metres -> Math.round(metres * 1000.0) / 1000.0 }
            )
        },
        Column("Fruit Maturity Stage") { text(it.maturityStage) },
        Column("Harvest Date") { text(it.harvestDate) },
        Column("Berry Diameter (mm)") { number(it.berryDiameter) },
        Column("TSS (Brix)") { number(it.tssBrix) },
        Column("Ease of Harvest") { text(it.easeOfHarvest) },
        Column("Fruit Shape") { text(it.fruitShape) },
        Column("Fruit Shape (Other)") { text(it.fruitShapeOther) },
        Column("Photo File Name") { text(it.photoFileName) },
        Column("Photo Path") { text(it.photoPath) },
        Column("Photo Captured At") { timestamp(it.photoCapturedAt) },
        Column("Status") { text(it.status) },
        Column("Created At") { timestamp(it.createdAt) },
        Column("Updated At") { timestamp(it.updatedAt) }
    )

    val headers: List<String> get() = all.map { it.header }

    fun row(survey: SurveyEntity): List<Cell> = all.map { it.value(survey) }
}
