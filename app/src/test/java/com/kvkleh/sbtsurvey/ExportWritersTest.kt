package com.kvkleh.sbtsurvey

import com.kvkleh.sbtsurvey.data.local.SurveyDao
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.export.CsvWriter
import com.kvkleh.sbtsurvey.export.SurveyColumns
import com.kvkleh.sbtsurvey.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExportWritersTest {

    private fun sample() = SurveyEntity(
        id = 1,
        surveyId = "SBT-2026-0001",
        surveyorName = "Stanzin, Khenrab",
        designation = "SMS \"Horticulture\"",
        organization = SurveyEntity.DEFAULT_ORGANIZATION,
        date = "2026-08-18",
        time = "09:30",
        district = "Leh",
        village = "Saboo",
        latitude = 34.152588,
        longitude = 77.577049,
        altitude = 3500.0,
        gpsAccuracy = 6.5f,
        gpsTimestamp = 1_700_000_000_000,
        plantHeight = 2.4,
        plantHeightUnit = "m",
        berryDiameter = 8.5,
        tssBrix = 12.4
    )

    @Test
    fun `csv quotes fields containing separators and quotes`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(listOf(sample()), out)
        val text = out.toString("UTF-8")

        assertTrue(text.contains("\"Stanzin, Khenrab\""))
        assertTrue(text.contains("\"SMS \"\"Horticulture\"\"\""))
    }

    @Test
    fun `csv header matches the shared column definition`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(emptyList(), out)
        val header = out.toString("UTF-8")
            .removePrefix("﻿")
            .lineSequence()
            .first()

        assertEquals(SurveyColumns.headers.size, header.split(",").size)
        assertTrue(header.startsWith("Survey ID,Surveyor Name"))
    }

    @Test
    fun `csv writes coordinates in full precision without scientific notation`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(listOf(sample()), out)
        val text = out.toString("UTF-8")

        assertTrue(text.contains("34.152588"))
        assertTrue(text.contains("77.577049"))
        assertTrue(!text.contains("E-"))
    }

    @Test
    fun `spreadsheet column names continue past Z`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AB", XlsxWriter.columnName(27))
        assertEquals("BA", XlsxWriter.columnName(52))
    }

    @Test
    fun `survey ids are zero padded and year scoped`() {
        assertEquals("SBT-2026-0001", SurveyDao.formatSurveyId(2026, 1))
        assertEquals("SBT-2026-0042", SurveyDao.formatSurveyId(2026, 42))
        assertEquals("SBT-2027-1234", SurveyDao.formatSurveyId(2027, 1234))
    }
}
