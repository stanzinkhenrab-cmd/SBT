package com.kvk.leh.seabuckthorn.data.backup

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kvk.leh.seabuckthorn.data.export.ExportStorage
import com.kvk.leh.seabuckthorn.data.local.AppDatabase
import com.kvk.leh.seabuckthorn.data.photo.PhotoStorage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates and restores a single-file, fully local backup archive containing the SQLite database
 * and every photo on disk. No part of this ever touches the network — the "backup" is just an
 * on-device (or SD-card / USB-transferable) file the user explicitly creates and restores.
 */
object BackupManager {
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private const val DB_ENTRY_PREFIX = "database/"
    private const val PHOTOS_ENTRY_PREFIX = "photos/"

    fun createBackup(context: Context, database: AppDatabase): File {
        // Flush the write-ahead log into the main database file so the backup is complete and consistent.
        database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()

        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        val photosDir = PhotoStorage.photosDir(context)
        val backupFile = File(ExportStorage.backupsDir(context), "seabuckthorn_backup_${timestampFormat.format(System.currentTimeMillis())}.sbtbackup")

        ZipOutputStream(FileOutputStream(backupFile)).use { zip ->
            addFile(zip, dbFile, "$DB_ENTRY_PREFIX${AppDatabase.DATABASE_NAME}")
            photosDir.listFiles()?.forEach { photo ->
                if (photo.isFile) addFile(zip, photo, "$PHOTOS_ENTRY_PREFIX${photo.name}")
            }
        }
        return backupFile
    }

    /**
     * Restores database and photos from a backup file. The caller MUST close the current
     * [AppDatabase] connection before calling this, and the app process must be restarted
     * afterwards for the restored database to be picked up cleanly.
     */
    fun restoreBackup(context: Context, backupFile: File) {
        val dbTargetDir = context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile
            ?: throw IllegalStateException("Could not resolve database directory")
        val photosDir = PhotoStorage.photosDir(context)

        ZipInputStream(FileInputStream(backupFile)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "$DB_ENTRY_PREFIX${AppDatabase.DATABASE_NAME}" -> {
                        writeEntryToFile(zip, File(dbTargetDir, AppDatabase.DATABASE_NAME))
                        // Discard any stale write-ahead log so SQLite doesn't replay it against the restored file.
                        File(dbTargetDir, "${AppDatabase.DATABASE_NAME}-wal").delete()
                        File(dbTargetDir, "${AppDatabase.DATABASE_NAME}-shm").delete()
                    }
                    name.startsWith(PHOTOS_ENTRY_PREFIX) && !entry.isDirectory -> {
                        val fileName = name.removePrefix(PHOTOS_ENTRY_PREFIX)
                        if (fileName.isNotBlank()) writeEntryToFile(zip, File(photosDir, fileName))
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeEntryToFile(zip: ZipInputStream, target: File) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out -> zip.copyTo(out) }
    }
}
