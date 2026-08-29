package com.sbt.geostamp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A thin wrapper over the platform [LocationManager].
 *
 * Deliberately avoids Google Play services so the app also works on devices without them;
 * accuracy is the same as long as the GPS provider is enabled.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Returns the best fix available within [timeoutMillis], falling back to the last known
     * position when no fresh fix arrives in time. Null means "no location at all".
     */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMillis: Long = 12_000L): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val fresh = withTimeoutOrNull(timeoutMillis) { awaitFix(manager) }
        return fresh ?: lastKnown(manager)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val providers = manager.getProviders(true)
                .filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
            if (providers.isEmpty()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }

                // Required on API < 30 or the listener is never registered on some OEM builds.
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }

            providers.forEach { provider ->
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            }

            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }
}
