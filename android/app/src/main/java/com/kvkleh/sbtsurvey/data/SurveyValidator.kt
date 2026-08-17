package com.kvkleh.sbtsurvey.data

import com.kvkleh.sbtsurvey.data.db.SurveyEntity

/** Identifies a field so the form can highlight exactly what is missing. */
object SurveyField {
    const val SURVEYOR_NAME = "surveyorName"
    const val DESIGNATION = "designation"
    const val ORGANIZATION = "organization"
    const val DISTRICT = "district"
    const val VILLAGE = "village"
    const val SHRUB_TYPE = "shrubType"
    const val PLANT_HEIGHT = "plantHeight"
    const val MATURITY_STAGE = "maturityStage"
    const val BERRY_DIAMETER = "berryDiameter"
    const val TSS = "tss"
    const val FRUIT_SHAPE = "fruitShape"
}

/**
 * Field-level rules for a survey record.
 *
 * Required fields are the ones a record is meaningless without. Everything else —
 * photo, GPS, harvest date, berry measurements — is reported as a warning on the
 * review screen, because a plot can legitimately be recorded before those numbers
 * are available and the data must never be blocked from being saved.
 */
object SurveyValidator {

    const val MAX_HEIGHT_METRES = 25.0
    const val MAX_HEIGHT_FEET = 82.0
    const val MAX_BERRY_DIAMETER_MM = 40.0
    const val MAX_TSS_BRIX = 60.0

    data class Result(
        val errors: Map<String, String>,
        val warnings: List<String>
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(
        survey: SurveyEntity,
        heightText: String = "",
        berryText: String = "",
        tssText: String = ""
    ): Result {
        val errors = LinkedHashMap<String, String>()
        val warnings = mutableListOf<String>()

        if (survey.surveyorName.isBlank()) {
            errors[SurveyField.SURVEYOR_NAME] = "Surveyor name is required"
        }
        if (survey.designation.isBlank()) {
            errors[SurveyField.DESIGNATION] = "Designation is required"
        }
        if (survey.organization.isBlank()) {
            errors[SurveyField.ORGANIZATION] = "Organization is required"
        }
        if (survey.district.isBlank()) {
            errors[SurveyField.DISTRICT] = "District is required"
        }
        if (survey.village.isBlank()) {
            errors[SurveyField.VILLAGE] = "Village is required"
        }
        if (survey.shrubType.isNullOrBlank()) {
            errors[SurveyField.SHRUB_TYPE] = "Select a shrub type"
        }
        if (survey.maturityStage.isNullOrBlank()) {
            errors[SurveyField.MATURITY_STAGE] = "Select the dominant fruit maturity stage"
        }

        val maxHeight = if (survey.plantHeightUnit == SurveyEntity.UNIT_FEET) {
            MAX_HEIGHT_FEET
        } else {
            MAX_HEIGHT_METRES
        }
        when {
            heightText.isBlank() && survey.plantHeight == null ->
                errors[SurveyField.PLANT_HEIGHT] = "Plant height is required"

            heightText.isNotBlank() && Formats.parseNumber(heightText) == null ->
                errors[SurveyField.PLANT_HEIGHT] = "Enter a number, for example 1.8"

            survey.plantHeight != null && survey.plantHeight <= 0.0 ->
                errors[SurveyField.PLANT_HEIGHT] = "Height must be greater than zero"

            survey.plantHeight != null && survey.plantHeight > maxHeight ->
                errors[SurveyField.PLANT_HEIGHT] =
                    "Height looks too large for ${survey.plantHeightUnit} (max $maxHeight)"
        }

        validateOptionalNumber(
            text = berryText,
            value = survey.berryDiameterMm,
            max = MAX_BERRY_DIAMETER_MM,
            field = SurveyField.BERRY_DIAMETER,
            label = "Berry diameter",
            unit = "mm",
            errors = errors
        )
        validateOptionalNumber(
            text = tssText,
            value = survey.tssBrix,
            max = MAX_TSS_BRIX,
            field = SurveyField.TSS,
            label = "TSS",
            unit = "°Brix",
            errors = errors
        )

        if (!survey.hasLocation) warnings += "GPS coordinates were not captured"
        if (survey.photoFileName.isNullOrBlank()) warnings += "No photo was taken"
        if (survey.harvestDate == null) warnings += "Harvest date was not entered"
        if (survey.berryDiameterMm == null) warnings += "Berry diameter was not measured"
        if (survey.tssBrix == null) warnings += "TSS (°Brix) was not measured"
        if (survey.fruitShape.isNullOrBlank()) warnings += "Fruit shape was not selected"
        if (survey.easeOfHarvest.isNullOrBlank()) warnings += "Ease of harvest was not selected"
        if (survey.block.isBlank()) warnings += "Block was not entered"
        if (survey.site.isBlank()) warnings += "Site was not entered"

        return Result(errors, warnings)
    }

    private fun validateOptionalNumber(
        text: String,
        value: Double?,
        max: Double,
        field: String,
        label: String,
        unit: String,
        errors: MutableMap<String, String>
    ) {
        if (text.isNotBlank() && Formats.parseNumber(text) == null) {
            errors[field] = "$label must be a number"
            return
        }
        if (value == null) return
        if (value <= 0.0) {
            errors[field] = "$label must be greater than zero"
        } else if (value > max) {
            errors[field] = "$label above ${Formats.number(max)} $unit looks like a typing mistake"
        }
    }
}
