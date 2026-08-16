package com.kvk.leh.seabuckthorn.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes a JPEG at a reduced resolution suitable for grid thumbnails, avoiding the memory cost
 * of loading full camera-resolution photos just to show a small preview. No external image
 * loading library is used — the app's photo set is small enough that this simple approach is
 * enough, and it keeps dependencies minimal.
 */
object BitmapUtils {
    fun decodeSampled(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var inSampleSize = 1
        val halfWidth = boundsOptions.outWidth / 2
        val halfHeight = boundsOptions.outHeight / 2
        while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSampleSize }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }
}
