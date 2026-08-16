package com.kvk.leh.seabuckthorn.domain.validation

/** Result of a single field validation: either valid, or invalid with a user-friendly message. */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

/**
 * Centralized, reusable scientific data validation rules. Every rule returns a friendly message
 * instead of throwing, so the UI can show it inline rather than crash or silently reject input.
 */
object Validators {

    fun latitude(value: Double?): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value in -90.0..90.0) ValidationResult.Valid
        else ValidationResult.Invalid("Latitude must be between -90 and +90 degrees")
    }

    fun longitude(value: Double?): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value in -180.0..180.0) ValidationResult.Valid
        else ValidationResult.Invalid("Longitude must be between -180 and +180 degrees")
    }

    fun nonNegative(value: Double?, fieldLabel: String): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value >= 0) ValidationResult.Valid
        else ValidationResult.Invalid("$fieldLabel cannot be negative")
    }

    fun positive(value: Double?, fieldLabel: String): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value > 0) ValidationResult.Valid
        else ValidationResult.Invalid("$fieldLabel must be greater than zero")
    }

    fun percentage(value: Double?, fieldLabel: String): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value in 0.0..100.0) ValidationResult.Valid
        else ValidationResult.Invalid("$fieldLabel must be between 0 and 100%")
    }

    /** Configurable realistic range check, e.g. TSS is normally 5-25 degrees Brix for seabuckthorn. */
    fun withinRange(value: Double?, min: Double, max: Double, fieldLabel: String): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value in min..max) ValidationResult.Valid
        else ValidationResult.Invalid("$fieldLabel of $value is outside the expected range ($min-$max). Please double-check.")
    }

    fun ph(value: Double?): ValidationResult {
        if (value == null) return ValidationResult.Valid
        return if (value in 0.0..14.0) ValidationResult.Valid
        else ValidationResult.Invalid("pH must be between 0 and 14")
    }

    /** Maturity stage percentages should sum to ~100% (small rounding tolerance allowed). */
    fun maturityPercentageTotal(total: Double, toleranceAbsolute: Double = 1.0): ValidationResult {
        return if (kotlin.math.abs(total - 100.0) <= toleranceAbsolute) ValidationResult.Valid
        else ValidationResult.Invalid("Maturity percentages total $total%, expected approximately 100%")
    }

    /** A survey date must not be in the future and not implausibly old. */
    fun surveyDateEpochDay(epochDay: Long, todayEpochDay: Long): ValidationResult {
        val earliestPlausible = todayEpochDay - (365L * 30) // 30 years back
        return when {
            epochDay > todayEpochDay -> ValidationResult.Invalid("Survey date cannot be in the future")
            epochDay < earliestPlausible -> ValidationResult.Invalid("Survey date is implausibly far in the past")
            else -> ValidationResult.Valid
        }
    }

    fun required(value: String?, fieldLabel: String): ValidationResult {
        return if (!value.isNullOrBlank()) ValidationResult.Valid
        else ValidationResult.Invalid("$fieldLabel is required")
    }
}

/** Realistic, configurable measurement ranges used to flag (not block) unusual field readings. */
object ScientificRanges {
    val tssBrix = 4.0..30.0
    val berryLengthMm = 2.0..20.0
    val berryDiameterMm = 2.0..20.0
    val berryWeightG = 0.05..3.0
    val plantHeightM = 0.1..6.0
}
