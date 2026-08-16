package com.kvk.leh.seabuckthorn.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Central place for date/time <-> storage-format conversions, all using the device's local zone. */
object DateUtils {
    private val displayDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val displayDateTimeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    fun nowTimeString(): String = LocalTime.now().format(timeFormat)

    fun epochDayToDisplay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(displayDateFormat)

    fun epochMillisToDisplayDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(displayDateTimeFormat)

    fun epochMillisToDisplayDate(millis: Long?): String =
        if (millis == null) "—" else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .toLocalDate().format(displayDateFormat)

    /** Converts an epoch-day date-picker selection (UTC millis at midnight) to a local epoch day. */
    fun utcMillisToEpochDay(utcMillis: Long): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()

    fun epochDayToUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    fun epochMillisToUtcMillisForPicker(epochMillis: Long?): Long? =
        epochMillis?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }

    fun localDateToEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
