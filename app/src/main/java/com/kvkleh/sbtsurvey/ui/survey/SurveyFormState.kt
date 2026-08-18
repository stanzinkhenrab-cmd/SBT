package com.kvkleh.sbtsurvey.ui.survey

import com.kvkleh.sbtsurvey.data.local.SurveyEntity

/** Where the autosave indicator is in its cycle. */
sealed interface AutoSaveState {
    data object Idle : AutoSaveState
    data object Saving : AutoSaveState
    data class Saved(val at: Long) : AutoSaveState
    data class Failed(val reason: String) : AutoSaveState
}

/** Field keys used to attach validation messages to inputs. */
object Field {
    const val SURVEYOR_NAME = "surveyorName"
    const val DESIGNATION = "designation"
    const val ORGANIZATION = "organization"
    const val DISTRICT = "district"
    const val VILLAGE = "village"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val ALTITUDE = "altitude"
    const val SHRUB_TYPE = "shrubType"
    const val PLANT_HEIGHT = "plantHeight"
    const val MATURITY_STAGE = "maturityStage"
    const val BERRY_DIAMETER = "berryDiameter"
    const val TSS = "tss"
    const val EASE_OF_HARVEST = "easeOfHarvest"
    const val FRUIT_SHAPE = "fruitShape"
    const val FRUIT_SHAPE_OTHER = "fruitShapeOther"
}

data class SurveyFormUiState(
    val loading: Boolean = true,
    val survey: SurveyEntity? = null,

    // Raw text kept separately from the parsed values so a half-typed number such as
    // "12." survives recomposition and autosave without being mangled.
    val plantHeightText: String = "",
    val berryDiameterText: String = "",
    val tssText: String = "",

    val organizationIsOther: Boolean = false,

    // Manual coordinate entry, for a handheld receiver reading, a map position, or a
    // point recorded on an earlier visit.
    val manualLocation: Boolean = false,
    val manualLatitudeText: String = "",
    val manualLongitudeText: String = "",
    val manualAltitudeText: String = "",

    val autoSave: AutoSaveState = AutoSaveState.Idle,
    val errors: Map<String, String> = emptyMap(),
    /** One-shot user message (error dialogs, save failures). */
    val message: String? = null,
    val finished: Boolean = false
) {
    val isEditingCompleted: Boolean get() = survey?.status == "completed"
}
