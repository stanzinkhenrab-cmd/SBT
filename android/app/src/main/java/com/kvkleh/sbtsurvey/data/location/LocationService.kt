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
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** A field position, as shown on the form and stored with the record. */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    /** Metres above the ellipsoid; `null` when the fix carries no altitude. */
    val altitude: Double?,
    val accuracyM: Double?,
    val capturedAt: Long,
    val provider: String
) {
    /**
     * True when the position came from the network rather than the satellites, or
     * is too coarse to place a shrub. Worth showing: on a tablet this is often the
     * only thing available, and the surveyor should know what they recorded.
     */
    val isApproximate: Boolean
        get() = provider == LocationManager.NETWORK_PROVIDER || (accuracyM ?: 0.0) > 100.0
}

/** What the GPS card shows the surveyor. */
sealed interface GpsStatus {
    /** Location permission has not been granted yet. */
    data object PermissionRequired : GpsStatus

    /** Location services are switched off on the device. */
    data object ServicesDisabled : GpsStatus

    /**
     * The device has no satellite receiver at all. Common on Wi-Fi-only tablets,
     * which is exactly the case this app used to spin on forever.
     */
    data object NoReceiver : GpsStatus

    /** Listening for a fix. */
    data class Acquiring(val elapsedSeconds: Int = 0) : GpsStatus

    /** A fix is available. */
    data class Ready(val fix: GpsFix) : GpsStatus

    /** Listening produced nothing usable; the record can still be saved. */
    data object Unavailable : GpsStatus
}

/**
 * Thin wrapper over the platform location APIs.
 *
 * Deliberately uses [LocationManager] rather than Google Play services: survey
 * devices in Ladakh are often without a network and sometimes without up-to-date
 * Play services, and the platform providers work with neither.
 *
 * Tablets are the awkward case and drive most of the behaviour here. Many are
 * Wi-Fi-only with no GNSS chip, or ship a receiver that takes minutes for a cold
 * fix. So the service reports what it actually knows - no receiver, listening for
 * n seconds, an approximate network position, or nothing after a timeout - rather
 * than showing "Acquiring GPS" indefinitely with no way to tell the difference.
 */
class LocationService(context: Context) {

    private val appContext = context.applicationContext

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(appContext, LocationManager::class.java)

    fun hasPermission(): Boolean = hasCoarsePermission() || hasFinePermission()

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Whether this device has a satellite receiver. A Wi-Fi-only tablet does not,
     * and can never produce a real fix however long it is left running.
     */
    fun hasGpsReceiver(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(manager) }.getOrDefault(false)
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
        if (manager == null) {
            trySend(GpsStatus.Unavailable)
            close()
            return@callbackFlow
        }
        if (!isLocationEnabled()) {
            trySend(GpsStatus.ServicesDisabled)
            close()
            return@callbackFlow
        }

        trySend(GpsStatus.Acquiring())

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
                        provider = location.provider ?: LocationManager.GPS_PROVIDER
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

        // Every provider the device actually offers, best first. PASSIVE picks up
        // fixes other apps obtain, which on a tablet is sometimes the only source.
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { provider ->
            runCatching {
                manager.allProviders.contains(provider) && manager.isProviderEnabled(provider)
            }.getOrDefault(false)
        }

        // Seed from the last known position so the card is never empty while the
        // receiver warms up; a live fix supersedes it as soon as one arrives.
        providers.forEach { provider ->
            runCatching { manager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.takeIf { System.currentTimeMillis() - it.time < LAST_KNOWN_MAX_AGE_MS }
                ?.let(::offer)
        }

        val registered = providers.count { provider ->
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

        if (registered == 0) {
            trySend(if (hasGpsReceiver()) GpsStatus.Unavailable else GpsStatus.NoReceiver)
        } else {
            // Report how long the search has been running, and give up saying so
            // rather than leaving "Acquiring" on screen forever. Listening
            // continues: a fix that arrives late is still recorded.
            launch {
                var elapsed = 0
                while (true) {
                    delay(TICK_MS)
                    elapsed += (TICK_MS / 1000).toInt()
                    if (best != null) return@launch
                    if (elapsed >= ACQUIRE_TIMEOUT_SECONDS) {
                        trySend(
                            if (hasGpsReceiver()) GpsStatus.Unavailable else GpsStatus.NoReceiver
                        )
                        return@launch
                    }
                    trySend(GpsStatus.Acquiring(elapsed))
                }
            }
        }

        awaitClose {
            runCatching { manager.removeUpdates(listener) }
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_000L
        const val MIN_DISTANCE_M = 0f
        const val STALE_AFTER_MS = 20_000L

        /**
         * A tablet that has been indoors may hold a fix from some time ago. Showing
         * it - clearly, with its timestamp - beats showing nothing, and the surveyor
         * can always tap Update Location once outside.
         */
        const val LAST_KNOWN_MAX_AGE_MS = 60 * 60 * 1000L

        const val TICK_MS = 5_000L

        /** A cold GNSS fix can take a minute; beyond that, say so. */
        const val ACQUIRE_TIMEOUT_SECONDS = 75
    }
}
