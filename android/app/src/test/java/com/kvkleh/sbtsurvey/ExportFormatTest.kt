package com.kvkleh.sbtsurvey

import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyDao
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.data.export.CsvWriter
import com.kvkleh.sbtsurvey.data.export.SurveyExportRow
import com.kvkleh.sbtsurvey.data.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExportFormatTest {

    private fun sampleSurvey() = SurveyEntity(
        id = 1,
        surveyId = "SBT-2026-0001",
        status = SurveyEntity.STATUS_SAVED,
        createdAt = 1_767_225_000_000L,
        updatedAt = 1_767_225_000_000L,
        surveyorName = "Stanzin Khenrab",
        designation = "SMS, Horticulture",
        organization = "KVK Leh",
        district = "Leh",
        block = "Kharu",
        village = "Sakti",
        site = "Riverbank plantation",
        photoPath = "/data/photos/SBT-2026-0001.jpg",
        photoFileName = "SBT-2026-0001.jpg",
        latitude = 34.152600,
        longitude = 77.577100,
        altitude = 3524.0,
        accuracyM = 4.0,
        shrubType = "Hardwood",
        plantHeight = 2.5,
        plantHeightUnit = SurveyEntity.UNIT_METRE,
        maturityStage = "Ripe",
        harvestDate = 1_767_225_000_000L,
        berryDiameterMm = 6.4,
        tssBrix = 11.2,
        easeOfHarvest = "Medium"
    )

    @Test
    fun `header and value counts match`() {
        assertEquals(SurveyExportRow.headers.size, SurveyExportRow.values(sampleSurvey()).size)
        assertEquals(SurveyExportRow.headers.size, SurveyExportRow.jsonKeys.size)
    }

    @Test
    fun `every requested column is present`() {
        val required = listOf(
            "Survey ID", "Surveyor Name", "Designation", "Organization", "Date", "Time",
            "District", "Block", "Village", "Site", "Photo Filename", "Latitude",
            "Longitude", "Altitude (m)", "Shrub Type", "Plant Height", "Plant Height Unit",
            "Dominant Fruit Maturity Stage", "Harvest Date", "Berry Diameter (mm)",
            "TSS (°Brix)", "Ease of Harvest"
        )
        required.forEach { column ->
            assertTrue("missing column: $column", SurveyExportRow.headers.contains(column))
        }
    }

    @Test
    fun `photo file name follows the survey id`() {
        val values = SurveyExportRow.values(sampleSurvey())
        val idIndex = SurveyExportRow.headers.indexOf("Survey ID")
        val photoIndex = SurveyExportRow.headers.indexOf("Photo Filename")
        assertEquals("${values[idIndex]}.jpg", values[photoIndex])
    }

    @Test
    fun `csv quotes separators and doubles quotation marks`() {
        assertEquals("plain", CsvWriter.escape("plain"))
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
    }

    @Test
    fun `csv export has one header row and one row per record`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(listOf(sampleSurvey(), sampleSurvey().copy(id = 2, surveyId = "SBT-2026-0002")), out)
        val text = out.toString("UTF-8").removePrefix("\uFEFF")
        val lines = text.trim().split("\r\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("Survey ID,"))
        assertTrue(lines[1].contains("SBT-2026-0001"))
        assertTrue(lines[2].contains("SBT-2026-0002"))
    }

    @Test
    fun `xlsx contains the workbook parts excel requires`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(listOf(sampleSurvey()), out)
        val entries = mutableListOf<String>()
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                entry = zip.nextEntry
            }
        }
        listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml"
        ).forEach { part ->
            assertTrue("missing part: $part", entries.contains(part))
        }
    }

    @Test
    fun `xlsx escapes the degree sign column and keeps numbers numeric`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(listOf(sampleSurvey()), out)
        var sheet = ""
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue(sheet.contains("TSS (°Brix)"))
        // Latitude is column L (index 11) and must be a bare numeric cell.
        assertTrue(sheet.contains("<c r=\"L2\"><v>34.152600</v></c>"))
    }

    @Test
    fun `spreadsheet column names roll over past z`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AB", XlsxWriter.columnName(27))
    }

    @Test
    fun `survey ids are zero padded and sortable`() {
        assertEquals("SBT-2026-0001", SurveyDao.formatSurveyId(2026, 1))
        assertEquals("SBT-2026-0042", SurveyDao.formatSurveyId(2026, 42))
        assertEquals("SBT-2026-1234", SurveyDao.formatSurveyId(2026, 1234))
    }

    @Test
    fun `numbers drop trailing zeros but keep decimals`() {
        assertEquals("2", Formats.number(2.0))
        assertEquals("2.5", Formats.number(2.5))
        assertEquals("11.25", Formats.number(11.25))
        assertEquals("", Formats.number(null))
    }

    @Test
    fun `decimal input accepts a comma separator`() {
        assertEquals(6.4, Formats.parseNumber("6,4"))
        assertEquals(6.4, Formats.parseNumber(" 6.4 "))
        assertEquals(null, Formats.parseNumber("abc"))
    }
}
