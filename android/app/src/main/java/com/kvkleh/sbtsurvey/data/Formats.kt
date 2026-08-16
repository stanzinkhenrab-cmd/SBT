package com.kvkleh.sbtsurvey.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date, time and number formatting shared by the UI and the exporters, so that a
 * value shown on screen and the same value in the CSV always read identically.
 */
object Formats {

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)

    fun date(millis: Long?): String =
        if (millis == null) "" else dateFormat.format(Date(millis))

    fun time(millis: Long?): String =
        if (millis == null) "" else timeFormat.format(Date(millis))

    fun dateTime(millis: Long?): String =
        if (millis == null) "" else dateTimeFormat.format(Date(millis))

    fun fileStamp(millis: Long): String = fileStampFormat.format(Date(millis))

    /** Formats a coordinate with six decimals, roughly 0.1 m of resolution. */
    fun coordinate(value: Double?): String =
        if (value == null) "" else String.format(Locale.US, "%.6f", value)

    fun metres(value: Double?): String =
        if (value == null) "" else String.format(Locale.US, "%.1f", value)

    /** Renders a number without a trailing `.0`, for round values such as `2`. */
    fun number(value: Double?): String {
        if (value == null) return ""
        return if (value == Math.floor(value) && !value.isInfinite()) {
            String.format(Locale.US, "%d", value.toLong())
        } else {
            String.format(Locale.US, "%s", trimZeros(value))
        }
    }

    private fun trimZeros(value: Double): String =
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    /** Parses a user-typed decimal, tolerating a comma decimal separator. */
    fun parseNumber(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    /**
     * The Material date picker reports UTC midnight. Convert it to local midnight
     * of the same calendar day, so a harvest date picked in Leh (UTC+5:30) is not
     * stored — or exported — as the previous day.
     */
    fun pickerMillisToLocalDate(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Inverse of [pickerMillisToLocalDate], used to pre-select a stored date. */
    fun localDateToPickerMillis(localMillis: Long?): Long? {
        if (localMillis == null) return null
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
    }

    /** Local midnight for the given instant, used to store harvest dates. */
    fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
