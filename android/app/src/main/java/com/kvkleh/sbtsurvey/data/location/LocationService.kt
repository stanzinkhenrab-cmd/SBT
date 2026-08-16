package com.kvkleh.sbtsurvey.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A field position, as shown on the form and stored with the record. */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    /** Metres above the ellipsoid; `null` when the fix carries no altitude. */
    val altitude: Double?,
    val accuracyM: Double?,
    val capturedAt: Long,
    val provider: String
)

/** What the GPS card shows the surveyor. */
sealed interface GpsStatus {
    /** Location permission has not been granted yet. */
    data object PermissionRequired : GpsStatus

    /** Location services are switched off on the device. */
    data object ServicesDisabled : GpsStatus

    /** Listening for a fix. */
    data object Acquiring : GpsStatus

    /** A fix is available. */
    data class Ready(val fix: GpsFix) : GpsStatus

    /** Listening produced nothing usable; the record can still be saved. */
    data object Unavailable : GpsStatus
}

/**
 * Thin wrapper over the platform location APIs.
 *
 * Deliberately uses [LocationManager] rather than Google Play services: survey
 * phones in Ladakh are often without a network and sometimes without up-to-date
 * Play services, and the platform GPS provider works with neither.
 */
class LocationService(context: Context) {

    private val appContext = context.applicationContext

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(appContext, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    /**
     * Emits status updates until the collector stops. Each new fix is only emitted
     * when it is at least as good as the best one seen so far, so the reading on
     * screen settles instead of flickering between providers.
     */
    @SuppressLint("MissingPermission") // guarded by the hasPermission() check below
    fun statusUpdates(): Flow<GpsStatus> = callbackFlow {
        if (!hasPermission()) {
            trySend(GpsStatus.PermissionRequired)
            close()
            return@callbackFlow
        }
        val manager = locationManager
        if (manager == null || !isLocationEnabled()) {
            trySend(GpsStatus.ServicesDisabled)
            close()
            return@callbackFlow
        }

        trySend(GpsStatus.Acquiring)

        var best: Location? = null

        fun offer(location: Location?) {
            if (location == null) return
            if (location.latitude == 0.0 && location.longitude == 0.0) return
            val current = best
            val isBetter = current == null ||
                location.time > current.time + STALE_AFTER_MS ||
                (location.hasAccuracy() &&
                    (!current.hasAccuracy() || location.accuracy <= current.accuracy))
            if (!isBetter) return
            best = location
            trySend(
                GpsStatus.Ready(
                    GpsFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                        capturedAt = if (location.time > 0) location.time else System.currentTimeMillis(),
                        provider = location.provider ?: "gps"
                    )
                )
            )
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = offer(location)

            @Deprecated("Required for API levels below 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // Seed from the last known position so the card is never empty while the
        // receiver warms up; a live fix supersedes it as soon as one arrives.
        providers.forEach { provider ->
            runCatching { manager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.takeIf { System.currentTimeMillis() - it.time < LAST_KNOWN_MAX_AGE_MS }
                ?.let(::offer)
        }

        val registered = providers.any { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    MIN_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper()
                )
                true
            }.getOrDefault(false)
        }

        if (!registered) trySend(GpsStatus.Unavailable)

        awaitClose {
            runCatching { manager.removeUpdates(listener) }
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_000L
        const val MIN_DISTANCE_M = 0f
        const val STALE_AFTER_MS = 20_000L
        const val LAST_KNOWN_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
