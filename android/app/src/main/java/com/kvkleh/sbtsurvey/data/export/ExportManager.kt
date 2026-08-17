package com.kvkleh.sbtsurvey.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kvkleh.sbtsurvey.data.photo.PhotoStore
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    CSV(
        label = "CSV",
        extension = "csv",
        mimeType = "text/csv",
        description = "Comma separated values. Opens in Excel, R, QGIS and SPSS."
    ),
    XLSX(
        label = "Excel",
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        description = "Formatted workbook with a frozen, filterable header row."
    ),
    JSON(
        label = "JSON",
        extension = "json",
        mimeType = "application/json",
        description = "Structured records for scripts and data pipelines."
    )
}

/** Result of a completed export, used to drive the confirmation message. */
data class ExportResult(
    val format: ExportFormat,
    val recordCount: Int,
    val fileName: String,
    val file: File?
)

/**
 * Builds export files and hands them to the platform, either through the system
 * file picker (Storage Access Framework) or the share sheet. Everything is written
 * on disk first, so a failed share never loses the file.
 */
class ExportManager(
    context: Context,
    private val appVersion: String,
    private val photoStore: PhotoStore
) {

    private val appContext = context.applicationContext

    private val exportDir: File
        get() = File(appContext.cacheDir, "exports").apply { if (!exists()) mkdirs() }

    fun suggestedFileName(format: ExportFormat, now: Long = System.currentTimeMillis()): String =
        "SBT-Survey-${Formats.fileStamp(now)}.${format.extension}"

    /** Writes the dataset into a cache file and returns it, ready to be shared. */
    suspend fun writeToCache(
        surveys: List<SurveyEntity>,
        format: ExportFormat
    ): ExportResult = withContext(Dispatchers.IO) {
        val name = suggestedFileName(format)
        val file = File(exportDir, name)
        file.outputStream().use { stream -> writeInto(surveys, format, stream) }
        ExportResult(format, surveys.size, name, file)
    }

    /** Writes the dataset straight into a location the user picked. */
    suspend fun writeToUri(
        surveys: List<SurveyEntity>,
        format: ExportFormat,
        uri: Uri
    ): ExportResult = withContext(Dispatchers.IO) {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            writeInto(surveys, format, stream)
        } ?: throw IllegalStateException("The selected location could not be opened for writing.")
        ExportResult(format, surveys.size, uri.lastPathSegment ?: suggestedFileName(format), null)
    }

    private fun writeInto(surveys: List<SurveyEntity>, format: ExportFormat, stream: OutputStream) {
        when (format) {
            ExportFormat.CSV -> CsvWriter.write(surveys, stream)
            ExportFormat.XLSX -> XlsxWriter.write(surveys, stream)
            ExportFormat.JSON -> JsonWriter.write(surveys, appVersion, stream)
        }
    }

    /**
     * Bundles the dataset and every photograph into one ZIP, so a whole survey
     * round can go out through WhatsApp or e-mail as a single attachment with the
     * images still matched to their rows by file name.
     */
    suspend fun writeBundleToCache(
        surveys: List<SurveyEntity>,
        format: ExportFormat
    ): ExportResult = withContext(Dispatchers.IO) {
        val stamp = Formats.fileStamp(System.currentTimeMillis())
        val name = "SBT-Survey-$stamp.zip"
        val file = File(exportDir, name)
        var photoCount = 0

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("SBT-Survey-$stamp.${format.extension}"))
            writeInto(surveys, format, zip)
            zip.closeEntry()

            surveys.forEach { survey ->
                val photo = photoStore.fileFor(survey.surveyId)
                if (photo.exists() && photo.length() > 0) {
                    zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    photoCount++
                }
            }

            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(bundleReadme(surveys.size, photoCount, format).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        ExportResult(format, surveys.size, name, file)
    }

    private fun bundleReadme(records: Int, photos: Int, format: ExportFormat): String =
        buildString {
            appendLine("Seabuckthorn Field Survey - Ladakh")
            appendLine("Krishi Vigyan Kendra - Leh, Ladakh | MIDH-SBM")
            appendLine()
            appendLine("Exported: ${Formats.dateTime(System.currentTimeMillis())}")
            appendLine("App version: $appVersion")
            appendLine("Records: $records")
            appendLine("Photographs: $photos")
            appendLine()
            appendLine("The data file is SBT-Survey-*.${format.extension}.")
            appendLine("Photographs are in photos/, named after the Survey ID, so each")
            appendLine("image matches the Photo Filename column of its row.")
        }

    fun shareIntent(file: File, format: ExportFormat): Intent {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".zip")) "application/zip" else format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Seabuckthorn Field Survey – Ladakh (${file.name})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Removes previously generated export files; they are only ever temporary copies. */
    fun clearCache() {
        runCatching { exportDir.listFiles()?.forEach { it.delete() } }
    }
}
