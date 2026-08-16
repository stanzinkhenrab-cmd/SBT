package com.kvk.leh.seabuckthorn.data.export

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** On-disk location for generated export/backup files, plus a shared timestamped filename helper. */
object ExportStorage {
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun exportsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun backupsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun timestampedFile(context: Context, prefix: String, extension: String): File =
        File(exportsDir(context), "${prefix}_${timestampFormat.format(System.currentTimeMillis())}.$extension")
}
