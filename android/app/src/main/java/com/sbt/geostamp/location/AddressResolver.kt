package com.sbt.geostamp.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Human-readable place description for a coordinate pair. */
data class PlaceLabel(val title: String, val addressLine: String)

/**
 * Reverse geocodes with the platform [Geocoder]. No API key and no network call of our own —
 * when the device has no geocoder backend we simply fall back to the raw coordinates.
 */
class AddressResolver(private val context: Context) {

    suspend fun resolve(latitude: Double, longitude: Double): PlaceLabel? {
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(8_000L) { lookup(latitude, longitude) } ?: return null
        return toLabel(address)
    }

    private suspend fun lookup(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1) }
                    .getOrNull()
                    ?.firstOrNull()
            }
        }
    }

    private fun toLabel(address: Address): PlaceLabel {
        val locality = address.locality
            ?: address.subLocality
            ?: address.subAdminArea
            ?: address.featureName.takeUnless { it.isNullOrBlank() || it == address.thoroughfare }

        // "Sumoor, Ladakh, India" — skip the blanks rather than printing stray commas.
        val title = listOfNotNull(locality, address.adminArea, address.countryName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val street = listOfNotNull(
            address.subThoroughfare,
            address.thoroughfare,
            address.subLocality
        ).filter { it.isNotBlank() }.distinct().joinToString(" ")

        val detail = listOfNotNull(
            street.takeIf { it.isNotBlank() },
            locality,
            address.adminArea,
            address.postalCode,
            address.countryName
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")

        val fullLine = address.getAddressLine(0).takeUnless { it.isNullOrBlank() } ?: detail
        return PlaceLabel(
            title = title.ifBlank { fullLine },
            addressLine = fullLine
        )
    }
}
