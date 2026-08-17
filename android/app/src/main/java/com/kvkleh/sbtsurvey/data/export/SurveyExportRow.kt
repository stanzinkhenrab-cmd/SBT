package com.kvkleh.sbtsurvey.data.export

import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyEntity

/**
 * The exported table definition. One place decides the column order and the value
 * for every cell, so CSV, Excel and JSON exports can never drift apart.
 */
object SurveyExportRow {

    /** Column headers, in export order. */
    val headers: List<String> = listOf(
        "Survey ID",
        "Surveyor Name",
        "Designation",
        "Organization",
        "Date",
        "Time",
        "District",
        "Block",
        "Village",
        "Site",
        "Photo Filename",
        "Latitude",
        "Longitude",
        "Altitude (m)",
        "GPS Accuracy (m)",
        "Shrub Type",
        "Plant Height",
        "Plant Height Unit",
        "Dominant Fruit Maturity Stage",
        "Harvest Date",
        "Fruit Shape",
        "Berry Diameter (mm)",
        "TSS (°Brix)",
        "Ease of Harvest",
        "Record Status"
    )

    /** JSON keys, in the same order as [headers]. */
    val jsonKeys: List<String> = listOf(
        "survey_id",
        "surveyor_name",
        "designation",
        "organization",
        "date",
        "time",
        "district",
        "block",
        "village",
        "site",
        "photo_filename",
        "latitude",
        "longitude",
        "altitude_m",
        "gps_accuracy_m",
        "shrub_type",
        "plant_height",
        "plant_height_unit",
        "dominant_fruit_maturity_stage",
        "harvest_date",
        "fruit_shape",
        "berry_diameter_mm",
        "tss_brix",
        "ease_of_harvest",
        "record_status"
    )

    /**
     * Column indices that hold numbers. Excel writes these as numeric cells so the
     * dataset can be charted or averaged without retyping.
     */
    val numericColumns: Set<Int> = setOf(11, 12, 13, 14, 16, 21, 22)

    fun values(survey: SurveyEntity): List<String> = listOf(
        survey.surveyId,
        survey.surveyorName,
        survey.designation,
        survey.organization,
        Formats.date(survey.createdAt),
        Formats.time(survey.createdAt),
        survey.district,
        survey.block,
        survey.village,
        survey.site,
        survey.photoFileName.orEmpty(),
        Formats.coordinate(survey.latitude),
        Formats.coordinate(survey.longitude),
        Formats.metres(survey.altitude),
        Formats.metres(survey.accuracyM),
        survey.shrubType.orEmpty(),
        Formats.number(survey.plantHeight),
        if (survey.plantHeight == null) "" else survey.plantHeightUnit,
        survey.maturityStage.orEmpty(),
        Formats.date(survey.harvestDate),
        survey.fruitShape.orEmpty(),
        Formats.number(survey.berryDiameterMm),
        Formats.number(survey.tssBrix),
        survey.easeOfHarvest.orEmpty(),
        if (survey.isDraft) "Draft" else "Saved"
    )
}
