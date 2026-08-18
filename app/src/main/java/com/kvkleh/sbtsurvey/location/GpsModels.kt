package com.kvkleh.sbtsurvey.location

/** A location fix retained by the app, in the form it is stored on a survey record. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Metres above the WGS-84 ellipsoid, null when the provider gave no altitude. */
    val altitude: Double?,
    /** Horizontal accuracy in metres, null when unknown. */
    val accuracy: Float?,
    /** Epoch millis at which this fix was produced. */
    val timestamp: Long
) {
    /** Lower is better; a fix with no accuracy is treated as very poor. */
    val rank: Float get() = accuracy ?: Float.MAX_VALUE
}

enum class GpsStatus {
    /** Nothing requested yet. */
    IDLE,

    /** Location permission has not been granted. */
    PERMISSION_REQUIRED,

    /** Device location services are switched off. */
    LOCATION_OFF,

    /** Listening for a fix; none retained yet. */
    ACQUIRING,

    /** A fix is retained but the app is still trying to improve it. */
    IMPROVING,

    /** A fix of acceptable accuracy is retained. */
    CONNECTED,

    /** Gave up for now; the surveyor can retry. */
    UNAVAILABLE
}

data class GpsState(
    val status: GpsStatus = GpsStatus.IDLE,
    val fix: LocationFix? = null,
    /** True while location updates are actively being requested. */
    val searching: Boolean = false,
    /** Seconds spent on the current acquisition attempt. */
    val elapsedSeconds: Int = 0,
    /** User-facing explanation, shown verbatim under the GPS card. */
    val message: String? = null
) {
    val hasFix: Boolean get() = fix != null

    val statusLabel: String
        get() = when (status) {
            GpsStatus.IDLE -> "Not started"
            GpsStatus.PERMISSION_REQUIRED -> "Permission needed"
            GpsStatus.LOCATION_OFF -> "Location off"
            GpsStatus.ACQUIRING -> "Searching…"
            GpsStatus.IMPROVING -> "Connected (improving)"
            GpsStatus.CONNECTED -> "Connected"
            GpsStatus.UNAVAILABLE -> "Unavailable"
        }
}
