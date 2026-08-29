package com.sbt.geostamp.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The visual layouts the stamp can be drawn in. */
enum class Template(val label: String, val description: String) {
    CLASSIC(
        "Classic",
        "Map thumbnail, place name, coordinates and QR code — the familiar GPS-camera card."
    ),
    COMPACT(
        "Compact",
        "A single translucent bar across the bottom with place, coordinates and time."
    ),
    RIBBON(
        "Ribbon",
        "Accent-coloured ribbon with large place name and a right-aligned detail column."
    ),
    POLAROID(
        "Polaroid",
        "Adds a printed footer below the photo instead of covering it."
    )
}

/** Everything that gets written onto the photo. */
data class StampContent(
    val title: String = "",
    val addressLine: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val timeMillis: Long = System.currentTimeMillis(),
    val note: String = "Captured by GeoStamp Camera"
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    fun formattedCoordinates(dms: Boolean): String {
        val lat = latitude ?: return ""
        val lon = longitude ?: return ""
        return if (dms) {
            "${toDms(lat, "N", "S")}  ${toDms(lon, "E", "W")}"
        } else {
            String.format(Locale.US, "Lat %.6f, Long %.6f", lat, lon)
        }
    }

    fun formattedDateTime(use24Hour: Boolean): String {
        val pattern = if (use24Hour) "EEEE, dd/MM/yyyy HH:mm" else "EEEE, dd/MM/yyyy hh:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMillis))
    }

    fun mapsUrl(): String? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon)
    }

    private fun toDms(value: Double, positive: String, negative: String): String {
        val hemisphere = if (value >= 0) positive else negative
        val abs = Math.abs(value)
        val degrees = abs.toInt()
        val minutesFull = (abs - degrees) * 60.0
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, hemisphere)
    }
}

/** Which parts of the stamp are drawn, and how. */
data class StampOptions(
    val template: Template = Template.CLASSIC,
    val showMap: Boolean = true,
    val showQr: Boolean = true,
    val showCoordinates: Boolean = true,
    val showDateTime: Boolean = true,
    val showNote: Boolean = true,
    val coordinatesAsDms: Boolean = false,
    val use24Hour: Boolean = true,
    val accentColor: Int = ACCENTS.first(),
    val mapZoom: Int = 14,
    val scale: Float = 1.0f
) {
    companion object {
        /** Accent swatches offered in the editor. */
        val ACCENTS = listOf(
            0xFF2E7D32.toInt(), // forest
            0xFF1565C0.toInt(), // ocean
            0xFFC62828.toInt(), // signal red
            0xFFEF6C00.toInt(), // amber
            0xFF4527A0.toInt(), // indigo
            0xFF00838F.toInt(), // teal
            0xFF212121.toInt()  // graphite
        )
    }
}
