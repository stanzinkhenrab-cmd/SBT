package com.kvkleh.sbtsurvey.domain

/**
 * Every option list in the survey form is modelled as an enum with a stable [storageValue].
 * The storage value is what is written to the database and to exports, so it must never change
 * once field data has been collected. [label] is display-only and may be reworded freely.
 */
interface SurveyOption {
    val storageValue: String
    val label: String
}

enum class ShrubType(override val storageValue: String, override val label: String) : SurveyOption {
    HARDWOOD("Hardwood", "Hardwood"),
    SOFTWOOD("Soft wood", "Soft wood"),
    MIXED("Mixed", "Mixed");

    companion object {
        fun fromStorage(value: String?): ShrubType? = entries.firstOrNull { it.storageValue == value }
    }
}

enum class MaturityStage(override val storageValue: String, override val label: String) : SurveyOption {
    UNRIPED("Unriped", "Unriped"),
    INTERMEDIATE("Intermediate", "Intermediate"),
    RIPENED("Ripened", "Ripened"),
    OVERRIPENED("Overripened", "Overripened");

    companion object {
        fun fromStorage(value: String?): MaturityStage? = entries.firstOrNull { it.storageValue == value }
    }
}

enum class EaseOfHarvest(override val storageValue: String, override val label: String) : SurveyOption {
    EASY("Easy", "Easy"),
    MEDIUM("Medium", "Medium"),
    HARD("Hard", "Hard");

    companion object {
        fun fromStorage(value: String?): EaseOfHarvest? = entries.firstOrNull { it.storageValue == value }
    }
}

enum class FruitShape(override val storageValue: String, override val label: String) : SurveyOption {
    ROUND("Round", "Round"),
    OVAL("Oval", "Oval"),
    OBLONG("Oblong", "Oblong"),
    ELLIPTICAL("Elliptical", "Elliptical"),
    CYLINDRICAL("Cylindrical", "Cylindrical"),
    OTHER("Other", "Other");

    companion object {
        fun fromStorage(value: String?): FruitShape? = entries.firstOrNull { it.storageValue == value }
    }
}

enum class HeightUnit(override val storageValue: String, override val label: String) : SurveyOption {
    METRE("m", "m"),
    FOOT("ft", "ft");

    companion object {
        const val FEET_PER_METRE = 3.280839895013123

        fun fromStorage(value: String?): HeightUnit = entries.firstOrNull { it.storageValue == value } ?: METRE
    }
}

/** Converts a height to metres regardless of the unit the surveyor typed it in. */
fun Double.toMetres(unit: HeightUnit): Double =
    if (unit == HeightUnit.METRE) this else this / HeightUnit.FEET_PER_METRE

/** Converts a height to feet regardless of the unit the surveyor typed it in. */
fun Double.toFeet(unit: HeightUnit): Double =
    if (unit == HeightUnit.FOOT) this else this * HeightUnit.FEET_PER_METRE

/** Where the coordinates on a record came from. */
enum class LocationSource(val storageValue: String, val label: String) {
    /** Acquired from the device's location provider. */
    GPS("gps", "Device GPS"),

    /** Typed in by the surveyor — from a handheld receiver, a map, or an earlier visit. */
    MANUAL("manual", "Entered manually");

    companion object {
        fun fromStorage(value: String?): LocationSource =
            entries.firstOrNull { it.storageValue == value } ?: GPS
    }
}

enum class SurveyStatus(val storageValue: String) {
    DRAFT("draft"),
    COMPLETED("completed");

    companion object {
        fun fromStorage(value: String?): SurveyStatus =
            entries.firstOrNull { it.storageValue == value } ?: DRAFT
    }
}
