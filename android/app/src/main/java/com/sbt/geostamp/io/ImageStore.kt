package com.sbt.geostamp.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes finished stamps to the gallery and prepares them for sharing. */
object ImageStore {

    private const val ALBUM = "GeoStamp"
    private const val QUALITY = 95

    /** Saves into Pictures/GeoStamp so the photo shows up in the gallery straight away. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = fileName()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/$ALBUM"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("The gallery refused a new image entry.")
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
                    } ?: error("Could not open the gallery entry for writing.")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } else {
                    @Suppress("DEPRECATION")
                    val album = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        ALBUM
                    )
                    if (!album.exists() && !album.mkdirs()) error("Could not create $ALBUM folder.")
                    val file = File(album, name)
                    FileOutputStream(file).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
                    }
                    // Nudge the media scanner so the gallery picks it up without a reboot.
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                    Uri.fromFile(file)
                }
            }
        }

    /** Writes a copy into cache and returns a shareable content:// uri. */
    suspend fun shareableUri(context: Context, bitmap: Bitmap): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
                val file = File(dir, fileName())
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }

    fun shareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "GeoStamp_$stamp.jpg"
    }
}
