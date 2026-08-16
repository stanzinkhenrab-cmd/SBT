package com.kvkleh.sbtsurvey.data.photo

import android.content.Context
import java.io.File

/**
 * Owns the on-device photo folder. One JPEG per survey, named after the survey
 * identifier (`SBT-2026-0001.jpg`), so an exported spreadsheet row and the image
 * on disk can always be matched by name alone.
 */
class PhotoStore(context: Context) {

    private val appContext = context.applicationContext

    val photoDir: File
        get() = File(appContext.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun fileNameFor(surveyId: String): String = "$surveyId.jpg"

    fun fileFor(surveyId: String): File = File(photoDir, fileNameFor(surveyId))

    /**
     * Destination used while the camera is writing. The capture is moved onto the
     * final name only once the image is complete, so an interrupted capture can
     * never leave a truncated `SBT-….jpg` behind.
     */
    fun pendingFileFor(surveyId: String): File = File(photoDir, "$surveyId.pending.jpg")

    fun commitPending(surveyId: String): File? {
        val pending = pendingFileFor(surveyId)
        if (!pending.exists() || pending.length() == 0L) return null
        val target = fileFor(surveyId)
        if (target.exists()) target.delete()
        return if (pending.renameTo(target)) target else null
    }

    fun discardPending(surveyId: String) {
        pendingFileFor(surveyId).takeIf { it.exists() }?.delete()
    }

    fun delete(surveyId: String) {
        fileFor(surveyId).takeIf { it.exists() }?.delete()
        discardPending(surveyId)
    }

    fun exists(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).let { it.exists() && it.length() > 0 }

    private companion object {
        const val DIR_NAME = "photos"
    }
}
