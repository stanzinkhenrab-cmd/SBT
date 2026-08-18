package com.kvkleh.sbtsurvey.export

import android.content.Context
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    CSV("CSV (.csv)", "csv", "text/csv"),
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PACKAGE("Complete Survey Package (.zip)", "zip", "application/zip")
}

/** Result of a successful export; [file] always exists and is non-empty. */
data class ExportResult(
    val file: File,
    val format: ExportFormat,
    val recordCount: Int,
    val photoCount: Int
)

/**
 * Builds the exportable artefacts (CSV, XLSX, or a complete ZIP package containing the
 * data file, every linked photograph and a README).
 *
 * Files are written into `cacheDir/exports`, which is exposed through the app's
 * FileProvider so the Android Sharesheet can hand them to any installed app.
 */
class ExportManager(
    private val context: Context,
    private val repository: SurveyRepository
) {

    private val exportDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun export(format: ExportFormat, surveys: List<SurveyEntity>? = null): ExportResult =
        withContext(Dispatchers.IO) {
            val records = surveys ?: repository.getAllOnce()
            check(records.isNotEmpty()) { "There are no surveys to export yet." }

            pruneOldExports()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

            when (format) {
                ExportFormat.CSV -> {
                    val file = File(exportDir, "SBT_Survey_$stamp.csv")
                    CsvWriter.write(records, file)
                    verify(file)
                    ExportResult(file, format, records.size, 0)
                }

                ExportFormat.XLSX -> {
                    val file = File(exportDir, "SBT_Survey_$stamp.xlsx")
                    XlsxWriter.write(records, file)
                    verify(file)
                    ExportResult(file, format, records.size, 0)
                }

                ExportFormat.PACKAGE -> buildPackage(records, stamp)
            }
        }

    private fun buildPackage(records: List<SurveyEntity>, stamp: String): ExportResult {
        val zipFile = File(exportDir, "SBT_Survey_Package_$stamp.zip")
        var photoCount = 0

        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data/SBT_Survey_$stamp.csv"))
            CsvWriter.write(records, zip)
            zip.closeEntry()

            val xlsxTemp = File(exportDir, ".tmp_$stamp.xlsx")
            XlsxWriter.write(records, xlsxTemp)
            zip.putNextEntry(ZipEntry("data/SBT_Survey_$stamp.xlsx"))
            xlsxTemp.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            xlsxTemp.delete()

            records.forEach { survey ->
                val path = survey.photoPath ?: return@forEach
                val photo = File(path)
                if (!photo.exists() || photo.length() == 0L) return@forEach
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                photoCount++
            }

            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(readme(records, photoCount, stamp).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        verify(zipFile)
        return ExportResult(zipFile, ExportFormat.PACKAGE, records.size, photoCount)
    }

    private fun readme(records: List<SurveyEntity>, photoCount: Int, stamp: String): String {
        val withGps = records.count { it.hasGps }
        return buildString {
            appendLine("Seabuckthorn Field Survey - Ladakh")
            appendLine("Krishi Vigyan Kendra - Leh, Ladakh | MIDH-SBM")
            appendLine("Developed by Stanzin Khenrab")
            appendLine()
            appendLine("DATASET")
            appendLine("  Exported on      : $stamp")
            appendLine("  Survey records   : ${records.size}")
            appendLine("  Records with GPS : $withGps")
            appendLine("  Photographs      : $photoCount")
            appendLine()
            appendLine("CONTENTS")
            appendLine("  data/    Survey records in CSV and Excel (.xlsx) form.")
            appendLine("           Both files contain identical columns in identical order.")
            appendLine("  photos/  Field photographs. The 'Photo File Name' column of the")
            appendLine("           data file links each record to its photograph in this folder.")
            appendLine()
            appendLine("COLUMNS")
            SurveyColumns.headers.forEach { appendLine("  - $it") }
            appendLine()
            appendLine("NOTES")
            appendLine("  Coordinates are WGS-84 decimal degrees. Altitude and GPS accuracy are")
            appendLine("  in metres, as reported by the device at the time of acquisition.")
            appendLine("  Plant height is recorded in the unit chosen by the surveyor; the column")
            appendLine("  'Plant Height (m)' holds the value converted to metres for analysis.")
            appendLine("  Berry diameter is in millimetres and TSS in degrees Brix.")
        }
    }

    /** Export verification: a zero-length or missing artefact is treated as a failure. */
    private fun verify(file: File) {
        check(file.exists() && file.length() > 0) {
            "Export verification failed: ${file.name} was not written correctly."
        }
    }

    /** Cache directory hygiene — exports older than a day are regenerated on demand. */
    private fun pruneOldExports() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        exportDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
