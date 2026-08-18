package com.kvkleh.sbtsurvey.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Permanent, app-private storage for survey photographs.
 *
 * Photographs are never referenced by a transient `content://` URI. The camera writes
 * straight into [photoDir] through a FileProvider URI, and gallery imports are copied in,
 * so the path stored on the survey record stays valid across reboots, app restarts and
 * revoked URI grants.
 */
class PhotoStore(private val context: Context) {

    val photoDir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Builds the destination file for a capture belonging to [surveyId]. */
    fun newPhotoFile(surveyId: String): File {
        val safeId = surveyId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        var candidate = File(photoDir, "${safeId}_${stamp()}.jpg")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(photoDir, "${safeId}_${stamp()}_$suffix.jpg")
            suffix++
        }
        return candidate
    }

    /** Shareable URI for a file inside app-private storage. */
    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Copies a picked image (gallery, files, cloud provider) into permanent storage.
     *
     * @return the destination file, or null when the source could not be read.
     */
    suspend fun importFrom(source: Uri, surveyId: String): File? = withContext(Dispatchers.IO) {
        val destination = newPhotoFile(surveyId)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                if (input == null) return@withContext null
                FileOutputStream(destination).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.fd.sync()
                }
            }
            destination
        } catch (e: IOException) {
            destination.delete()
            null
        } catch (e: SecurityException) {
            destination.delete()
            null
        }
    }

    /** Deletes a stored photograph; safe to call with a null or already-missing path. */
    suspend fun delete(path: String?): Unit = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        val file = File(path)
        // Only ever delete inside our own photo directory.
        if (file.exists() && file.parentFile?.absolutePath == photoDir.absolutePath) {
            file.delete()
        }
    }

    fun exists(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).let { it.exists() && it.length() > 0 }

    companion object {
        private const val DIR_NAME = "survey_photos"
    }
}
