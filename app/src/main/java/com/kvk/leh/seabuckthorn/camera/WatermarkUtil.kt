package com.kvk.leh.seabuckthorn.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Optional in-place watermark: burns "Survey ID | Date | Latitude | Longitude | Altitude" onto
 * the bottom of a captured JPEG. Only ever invoked when the user explicitly enables it for a
 * photo — the original file is otherwise left untouched.
 */
object WatermarkUtil {

    fun applyWatermark(photoFile: File, lines: List<String>) {
        val orientation = runCatching {
            ExifInterface(photoFile.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val original = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()

        val canvas = Canvas(bitmap)
        val textSizePx = bitmap.width * 0.022f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        val backgroundPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }

        val padding = textSizePx * 0.6f
        val lineHeight = textSizePx * 1.35f
        val blockHeight = lineHeight * lines.size + padding * 2
        val top = bitmap.height - blockHeight
        canvas.drawRect(Rect(0, top.toInt(), bitmap.width, bitmap.height), backgroundPaint)

        var y = top + padding + textSizePx
        for (line in lines) {
            canvas.drawText(line, padding, y, paint)
            y += lineHeight
        }

        FileOutputStream(photoFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()

        // Preserve orientation metadata on the re-encoded file so the watermark isn't sideways.
        runCatching {
            ExifInterface(photoFile.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
    }

    fun watermarkLines(surveyCode: String, dateText: String, latitude: Double?, longitude: Double?, altitude: Double?): List<String> {
        val coords = if (latitude != null && longitude != null) {
            "Lat ${"%.5f".format(latitude)}, Lon ${"%.5f".format(longitude)}"
        } else {
            "Lat/Lon unavailable"
        }
        val alt = altitude?.let { "Alt ${"%.0f".format(it)} m" } ?: "Alt unavailable"
        return listOf(
            "Survey ID: $surveyCode",
            "Date: $dateText",
            "$coords  |  $alt"
        )
    }
}
