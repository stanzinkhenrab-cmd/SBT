package com.kvk.leh.seabuckthorn.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Snapshot of a GPS fix relevant to field survey capture. */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val acquiredAtMillis: Long
) {
    companion object {
        fun fromLocation(location: Location) = GpsFix(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            acquiredAtMillis = System.currentTimeMillis()
        )
    }
}

/**
 * Wraps the platform's [LocationManager] directly (GPS_PROVIDER only) rather than a Google
 * Play Services fused location client, so location capture never depends on Play Services or
 * network connectivity — it works on any Android device with a GPS chip, fully offline.
 */
class LocationTracker(private val context: Context) {

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isGpsProviderEnabled(): Boolean = runCatching {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }.getOrDefault(false)

    fun lastKnownFix(): GpsFix? {
        if (!hasLocationPermission()) return null
        return runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { GpsFix.fromLocation(it) }
        }.getOrNull()
    }

    /**
     * Emits a new [GpsFix] every time the GPS chip reports an updated position, so the UI can
     * show a live-improving "GPS Accuracy: ±X m" readout. The caller decides when a fix is
     * accurate enough to lock in, or stops collecting on a manual/auto timeout.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun observeFixes(): Flow<GpsFix> = callbackFlow {
        if (!hasLocationPermission() || !isGpsProviderEnabled()) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(GpsFix.fromLocation(location))
            }
            @Deprecated("Deprecated in platform API, override required pre-API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) {
                close()
            }
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            /* minTimeMs = */ 1000L,
            /* minDistanceM = */ 0f,
            listener,
            Looper.getMainLooper()
        )
        awaitClose { locationManager.removeUpdates(listener) }
    }
}
