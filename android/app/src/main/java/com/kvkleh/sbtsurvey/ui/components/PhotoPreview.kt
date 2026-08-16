package com.kvkleh.sbtsurvey.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads a survey photo for on-screen preview.
 *
 * The full capture is several megapixels; only a screen-sized copy is decoded so
 * that scrolling the form stays smooth and the app never trips the heap limit on
 * an entry-level field phone.
 */
@Composable
fun rememberSurveyPhoto(path: String?, maxDimension: Int = 1280): ImageBitmap? {
    var bitmap by remember(path, maxDimension) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path, maxDimension) {
        if (path.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) { decodeScaled(File(path), maxDimension) }
        bitmap = decoded?.asImageBitmap()
    }
    return bitmap
}

private fun decodeScaled(file: File, maxDimension: Int): Bitmap? {
    if (!file.exists() || file.length() == 0L) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension ||
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }

        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        applyExifRotation(file, bitmap)
    }.getOrNull()
}

/** Cameras record orientation in EXIF rather than rotating the pixels. */
private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}
