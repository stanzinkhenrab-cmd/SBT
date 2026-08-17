package com.kvkleh.sbtsurvey.data.photo

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Copies a survey photograph into the device's own picture gallery.
 *
 * The app keeps its working copy in private storage, where nothing else can
 * disturb it. That copy is invisible to the surveyor, though: it does not appear
 * in Gallery or Photos and is not picked up by a phone backup. So when a survey is
 * saved, the image is also published to `Pictures/Seabuckthorn Survey/`, under the
 * Survey ID, where the surveyor can see it, back it up and share it like any other
 * photo.
 *
 * Publishing is best effort. It never blocks or fails a save: the record and the
 * private copy are the source of truth.
 */
class GalleryExporter(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Publishes the photo for [surveyId]. Returns true when a copy now exists in
     * the gallery.
     */
    fun publish(surveyId: String, source: File): Boolean {
        if (!source.exists() || source.length() == 0L) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(surveyId, source)
            } else {
                publishToLegacyPicturesFolder(surveyId, source)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not publish $surveyId to the gallery", error)
            false
        }
    }

    /**
     * Android 10 and newer: hand the bytes to MediaStore, which owns the folder.
     * No storage permission is required for the app's own media.
     */
    private fun publishViaMediaStore(surveyId: String, source: File): Boolean {
        val resolver = appContext.contentResolver
        val fileName = "$surveyId.jpg"

        // Replace an earlier copy so a retaken photo does not leave two versions.
        runCatching {
            resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf("$RELATIVE_PATH/", fileName)
            )
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: return false

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    /**
     * Android 9 and older: write into the public Pictures folder directly, then
     * ask the media scanner to index it so Gallery shows it without a reboot.
     */
    @Suppress("DEPRECATION")
    private fun publishToLegacyPicturesFolder(surveyId: String, source: File): Boolean {
        val pictures = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val folder = File(pictures, FOLDER_NAME)
        if (!folder.exists() && !folder.mkdirs()) return false

        val target = File(folder, "$surveyId.jpg")
        source.copyTo(target, overwrite = true)

        // MediaScannerConnection lives in android.media; calling it through the
        // broadcast keeps this free of a lifecycle-bound connection.
        runCatching {
            android.media.MediaScannerConnection.scanFile(
                appContext,
                arrayOf(target.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
        }
        return target.exists()
    }

    /** Where a surveyor will find the published photos, for display in the app. */
    val galleryFolderLabel: String get() = "Pictures/$FOLDER_NAME"

    private companion object {
        const val TAG = "GalleryExporter"
        const val FOLDER_NAME = "Seabuckthorn Survey"
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME"
    }
}
