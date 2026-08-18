package com.kvkleh.sbtsurvey.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Acquires and retains the best available GPS fix.
 *
 * Written for tablets as much as phones: a tablet without a cellular modem can take a
 * long time for its first fix and often reports poor accuracy, so this controller
 *
 *  * keeps listening in the background instead of blocking the UI,
 *  * reports progress (elapsed seconds, live accuracy) so the surveyor can see it working,
 *  * retains the *best* fix seen and never replaces it with a worse one, and
 *  * only starts from scratch when the surveyor explicitly refreshes.
 *
 * All Play-services calls are wrapped: a device without Google Play services, with the
 * camera/GPS hardware missing, or with the permission revoked mid-session degrades to a
 * clear message rather than a crash.
 */
class LocationController(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()

    private var callback: LocationCallback? = null
    private var timerJob: Job? = null
    private var attemptStartedAt: Long = 0L

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val isLocationEnabled: Boolean
        get() = runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

    /** Seeds state from a fix already stored on the survey record being edited. */
    fun adoptSavedFix(fix: LocationFix?) {
        if (fix == null || _state.value.fix != null) return
        _state.value = _state.value.copy(
            fix = fix,
            status = if (fix.rank <= GOOD_ACCURACY_M) GpsStatus.CONNECTED else GpsStatus.IMPROVING
        )
    }

    /**
     * Starts (or continues) acquisition.
     *
     * @param force true when the surveyor pressed Refresh GPS: the retained fix is dropped
     *              so a genuinely new reading is taken.
     */
    fun start(force: Boolean = false) {
        if (!hasPermission) {
            stopUpdates()
            _state.value = _state.value.copy(
                status = GpsStatus.PERMISSION_REQUIRED,
                searching = false,
                message = "Location permission is required to record survey coordinates."
            )
            return
        }
        if (!isLocationEnabled) {
            stopUpdates()
            _state.value = _state.value.copy(
                status = GpsStatus.LOCATION_OFF,
                searching = false,
                message = "Device location is switched off. Turn on Location, then press Refresh GPS."
            )
            return
        }

        if (force) {
            stopUpdates()
            _state.value = GpsState(status = GpsStatus.ACQUIRING, searching = true)
        } else if (_state.value.searching) {
            return  // already listening
        }

        attemptStartedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(
            status = if (_state.value.fix == null) GpsStatus.ACQUIRING else GpsStatus.IMPROVING,
            searching = true,
            elapsedSeconds = 0,
            message = null
        )

        seedFromLastKnown(force)
        requestUpdates()
        startTimer()
    }

    /** Explicit user refresh: discards the retained fix and takes a new reading. */
    fun refresh() = start(force = true)

    /** Stops listening; the retained fix is kept. */
    fun stop() {
        stopUpdates()
        _state.value = _state.value.copy(searching = false)
    }

    // --- internals ------------------------------------------------------------

    private fun seedFromLastKnown(force: Boolean) {
        if (force) return
        runCatching {
            fused.lastLocation.addOnSuccessListener { location ->
                if (location == null) return@addOnSuccessListener
                val age = System.currentTimeMillis() - location.time
                if (age in 0..LAST_KNOWN_MAX_AGE_MS) {
                    offer(
                        LocationFix(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            accuracy = if (location.hasAccuracy()) location.accuracy else null,
                            timestamp = location.time
                        )
                    )
                }
            }
        }
    }

    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    offer(
                        LocationFix(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            accuracy = if (location.hasAccuracy()) location.accuracy else null,
                            timestamp = if (location.time > 0) location.time else System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        callback = cb

        val requested = runCatching {
            fused.requestLocationUpdates(request, cb, Looper.getMainLooper())
        }
        if (requested.isFailure) {
            callback = null
            _state.value = _state.value.copy(
                status = GpsStatus.UNAVAILABLE,
                searching = false,
                message = "Location services are not available on this device. " +
                    "Survey data can still be saved without coordinates."
            )
        }
    }

    /** Accepts a candidate fix only when it improves on what is already retained. */
    private fun offer(candidate: LocationFix) {
        val current = _state.value
        val existing = current.fix
        val accept = existing == null ||
            candidate.rank < existing.rank ||
            (candidate.timestamp - existing.timestamp) > STALE_FIX_MS
        if (!accept) {
            // A worse reading is discarded outright; the retained fix is what gets saved.
            if (current.message != null) _state.value = current.copy(message = null)
            return
        }

        val goodEnough = candidate.rank <= GOOD_ACCURACY_M
        _state.value = current.copy(
            fix = candidate,
            status = if (goodEnough) GpsStatus.CONNECTED else GpsStatus.IMPROVING,
            message = null
        )
        if (goodEnough) {
            // Accurate enough for a field record: stop draining the battery.
            stopUpdates()
            _state.value = _state.value.copy(searching = false)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _state.value.searching) {
                delay(1_000)
                val elapsed = ((System.currentTimeMillis() - attemptStartedAt) / 1_000).toInt()
                val current = _state.value
                if (!current.searching) break

                _state.value = current.copy(elapsedSeconds = elapsed)

                if (elapsed >= ACQUISITION_TIMEOUT_S) {
                    stopUpdates()
                    _state.value = _state.value.copy(
                        searching = false,
                        status = if (_state.value.fix != null) {
                            GpsStatus.CONNECTED
                        } else {
                            GpsStatus.UNAVAILABLE
                        },
                        message = if (_state.value.fix != null) {
                            "Best available reading kept. Press Refresh GPS for a new fix."
                        } else {
                            "Unable to obtain GPS location. Please move to an open area " +
                                "and try Refresh GPS."
                        }
                    )
                    break
                }
            }
        }
    }

    private fun stopUpdates() {
        timerJob?.cancel()
        timerJob = null
        callback?.let { cb ->
            runCatching { fused.removeLocationUpdates(cb) }
        }
        callback = null
    }

    companion object {
        /** Accuracy at or below which a reading is considered good enough to stop searching. */
        const val GOOD_ACCURACY_M = 10f

        private const val UPDATE_INTERVAL_MS = 2_000L
        private const val FASTEST_INTERVAL_MS = 1_000L

        /** A retained fix older than this is replaced even by a less accurate reading. */
        private const val STALE_FIX_MS = 5 * 60 * 1_000L

        /** Cached fixes older than this are ignored when seeding. */
        private const val LAST_KNOWN_MAX_AGE_MS = 2 * 60 * 1_000L

        /** Tablets can need well over a minute for a cold fix. */
        private const val ACQUISITION_TIMEOUT_S = 120
    }
}
