package com.kvk.leh.seabuckthorn.data.photo

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Owns the on-disk layout for survey photographs. Files live under app-specific external
 * storage (no permission needed on API 24+, not visible to other apps, removed on uninstall),
 * so photo capture and viewing work fully offline with no user-facing storage setup.
 */
object PhotoStorage {

    private const val PHOTOS_DIR = "photos"
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun photosDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, PHOTOS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newPhotoFile(context: Context, surveyCode: String, categoryName: String, photoNumber: Int): File {
        val safeSurveyCode = surveyCode.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val timestamp = fileTimestampFormat.format(System.currentTimeMillis())
        val fileName = "${safeSurveyCode}_${categoryName}_${photoNumber}_$timestamp.jpg"
        return File(photosDir(context), fileName)
    }

    fun deleteFile(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
