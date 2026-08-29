package com.sbt.geostamp.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Location + capture time recovered from a photo's EXIF block. */
data class PhotoExif(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val timeMillis: Long? = null
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** Decodes a picked or captured image into a right-way-up, memory-safe bitmap. */
object PhotoLoader {

    /** Long-edge cap; keeps 12 MP captures editable without blowing the heap. */
    const val MAX_DIMENSION = 4096

    suspend fun load(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null

        val rotation = openStream(context, uri)?.use { stream ->
            runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
        } ?: 0

        if (rotation == 0) decoded else rotate(decoded, rotation)
    }

    suspend fun readExif(context: Context, uri: Uri): PhotoExif = withContext(Dispatchers.IO) {
        val exif = openStream(context, uri)?.use { stream ->
            runCatching { ExifInterface(stream) }.getOrNull()
        } ?: return@withContext PhotoExif()

        val coordinates = runCatching { exif.latLong }.getOrNull()
        val altitude = runCatching { exif.getAltitude(Double.NaN) }.getOrNull()
            ?.takeIf { !it.isNaN() }
        val timestamp = runCatching { exif.dateTimeOriginal ?: exif.dateTime }.getOrNull()

        PhotoExif(
            latitude = coordinates?.getOrNull(0),
            longitude = coordinates?.getOrNull(1),
            altitudeMeters = altitude,
            timeMillis = timestamp
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun openStream(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
}
