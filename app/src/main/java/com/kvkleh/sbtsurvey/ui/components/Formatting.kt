package com.kvkleh.sbtsurvey.ui.components

import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.domain.HeightUnit
import com.kvkleh.sbtsurvey.domain.toFeet
import com.kvkleh.sbtsurvey.domain.toMetres
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Display formatting shared by the dashboard, detail screen and map. */
object Fmt {

    private val displayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val displayDateTime = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching { isoDate.parse(iso)?.let { displayDate.format(it) } }.getOrNull() ?: iso
    }

    fun dateTime(millis: Long?): String =
        if (millis == null || millis <= 0) "—" else displayDateTime.format(Date(millis))

    fun coordinate(value: Double?): String =
        if (value == null) "—" else String.format(Locale.US, "%.6f", value)

    fun metres(value: Double?): String =
        if (value == null) "—" else String.format(Locale.US, "%.0f m", value)

    fun accuracy(value: Float?): String =
        if (value == null) "—" else String.format(Locale.US, "±%.0f m", value)

    fun decimal(value: Double?, unit: String = "", decimals: Int = 1): String {
        if (value == null) return "—"
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if (unit.isBlank()) text else "$text $unit"
    }

    /** "2.40 m (7.87 ft)" — both units, so a reader never has to convert by hand. */
    fun height(survey: SurveyEntity): String {
        val value = survey.plantHeight ?: return "—"
        val unit = HeightUnit.fromStorage(survey.plantHeightUnit)
        val metres = value.toMetres(unit)
        val feet = value.toFeet(unit)
        return String.format(Locale.US, "%.2f m (%.2f ft)", metres, feet)
    }

    fun fruitShape(survey: SurveyEntity): String {
        val shape = survey.fruitShape ?: return "—"
        val other = survey.fruitShapeOther
        return if (shape == "Other" && !other.isNullOrBlank()) "Other – $other" else shape
    }
}
