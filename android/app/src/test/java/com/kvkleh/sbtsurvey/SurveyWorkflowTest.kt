package com.kvkleh.sbtsurvey

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kvkleh.sbtsurvey.data.SurveyValidator
import com.kvkleh.sbtsurvey.data.db.SurveyDatabase
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.data.export.CsvWriter
import com.kvkleh.sbtsurvey.data.export.SurveyExportRow
import com.kvkleh.sbtsurvey.data.export.XlsxWriter
import com.kvkleh.sbtsurvey.data.photo.PhotoStore
import com.kvkleh.sbtsurvey.data.prefs.LastLocation
import com.kvkleh.sbtsurvey.data.prefs.SurveyPreferences
import com.kvkleh.sbtsurvey.data.prefs.SurveyorProfile
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Walks the whole field workflow through the storage layer:
 * new survey → surveyor → location → photo → GPS → plant data → auto-save →
 * save → view → export → delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SurveyWorkflowTest {

    private lateinit var database: SurveyDatabase
    private lateinit var repository: SurveyRepository
    private lateinit var photoStore: PhotoStore
    private lateinit var preferences: SurveyPreferences

    private val profile = SurveyorProfile(
        name = "Stanzin Khenrab",
        designation = "SMS Horticulture",
        organization = "KVK Leh"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photoStore = PhotoStore(context)
        preferences = SurveyPreferences(context)
        repository = SurveyRepository(database.surveyDao(), photoStore, preferences)
        photoStore.photoDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeFieldWorkflow() = runBlocking {
        // New Survey — the record exists on disk before a single field is typed.
        val rowId = repository.startDraft(profile, LastLocation())
        val draft = repository.getById(rowId)!!
        assertTrue(draft.isDraft)
        assertTrue(draft.surveyId.startsWith("SBT-"))
        assertTrue(draft.createdAt > 0)
        assertEquals("KVK Leh", draft.organization)
        assertEquals(0, repository.observeSavedCount().first())

        // Location, plant data and GPS, written the way auto-save writes them.
        var working = draft.copy(
            district = "Leh",
            block = "Kharu",
            village = "Sakti",
            site = "Riverbank plantation",
            latitude = 34.1526,
            longitude = 77.5771,
            altitude = 3524.0,
            accuracyM = 4.0,
            locationCapturedAt = System.currentTimeMillis(),
            shrubType = "Hardwood",
            plantHeight = 2.4,
            maturityStage = "Ripe",
            harvestDate = System.currentTimeMillis(),
            berryDiameterMm = 6.4,
            tssBrix = 11.2,
            easeOfHarvest = "Medium"
        )
        repository.saveDraft(working)
        assertEquals("Sakti", repository.getById(rowId)!!.village)

        // Photo capture: written to a pending file, then committed under the ID.
        photoStore.pendingFileFor(draft.surveyId).writeBytes(byteArrayOf(1, 2, 3, 4))
        val photo = photoStore.commitPending(draft.surveyId)!!
        assertEquals("${draft.surveyId}.jpg", photo.name)
        repository.attachPhoto(rowId, photo.absolutePath, photo.name)
        working = repository.getById(rowId)!!
        assertEquals("${draft.surveyId}.jpg", working.photoFileName)

        // Review: valid, and warns about nothing since every field was filled in.
        val validation = SurveyValidator.validate(working, heightText = "2.4")
        assertTrue(validation.errors.toString(), validation.isValid)
        assertTrue(validation.warnings.isEmpty())

        // Save.
        val saved = repository.commit(working)
        assertEquals(SurveyEntity.STATUS_SAVED, saved.status)
        assertEquals(1, repository.observeSavedCount().first())
        assertNull(repository.getLatestDraft())

        // The surveyor and the location are remembered for the next survey.
        assertEquals("Stanzin Khenrab", preferences.profile.value.name)
        assertEquals("Leh", preferences.lastLocation.value.district)
        val next = repository.getById(
            repository.startDraft(preferences.profile.value, preferences.lastLocation.value)
        )!!
        assertEquals("Stanzin Khenrab", next.surveyorName)
        assertEquals("Kharu", next.block)
        assertTrue(next.surveyId != saved.surveyId)

        // View: saved records only, newest first.
        val visible = repository.observeSaved().first()
        assertEquals(1, visible.size)
        assertEquals(saved.surveyId, visible.first().surveyId)

        // Export: the saved row carries every value through to the file.
        val out = ByteArrayOutputStream()
        CsvWriter.write(visible, out)
        val csv = out.toString("UTF-8").removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals(2, csv.size)
        val header = csv[0].split(",")
        val row = csv[1].split(",")
        fun cell(column: String) = row[header.indexOf(column)]
        assertEquals(saved.surveyId, cell("Survey ID"))
        assertEquals("Sakti", cell("Village"))
        assertEquals("34.152600", cell("Latitude"))
        assertEquals("3524.0", cell("Altitude (m)"))
        assertEquals("Hardwood", cell("Shrub Type"))
        assertEquals("2.4", cell("Plant Height"))
        assertEquals("m", cell("Plant Height Unit"))
        assertEquals("Ripe", cell("Dominant Fruit Maturity Stage"))
        assertEquals("6.4", cell("Berry Diameter (mm)"))
        assertEquals("11.2", cell("TSS (°Brix)"))
        assertEquals("Medium", cell("Ease of Harvest"))
        assertEquals("${saved.surveyId}.jpg", cell("Photo Filename"))
        assertEquals(SurveyExportRow.headers.size, row.size)

        val workbook = ByteArrayOutputStream()
        XlsxWriter.write(visible, workbook)
        assertTrue(workbook.size() > 0)

        // Delete: the record and its image go together, the other survey stays.
        repository.delete(saved)
        assertFalse(photoStore.fileFor(saved.surveyId).exists())
        assertNull(repository.getById(saved.id))
        assertEquals(0, repository.observeSavedCount().first())
        assertEquals(next.surveyId, repository.getLatestDraft()?.surveyId)
    }

    @Test
    fun aRecordWithoutGpsOrPhotoStillSaves() = runBlocking {
        val rowId = repository.startDraft(profile, LastLocation())
        val working = repository.getById(rowId)!!.copy(
            district = "Kargil",
            village = "Sankoo",
            shrubType = "Mixed",
            plantHeight = 1.6,
            maturityStage = "Unripe"
        )
        repository.saveDraft(working)

        val validation = SurveyValidator.validate(working, heightText = "1.6")
        assertTrue(validation.isValid)
        assertTrue(validation.warnings.any { it.contains("GPS") })

        val saved = repository.commit(working)
        assertEquals(SurveyEntity.STATUS_SAVED, saved.status)
        assertFalse(saved.hasLocation)

        // Coordinates can be added later without touching anything else.
        repository.saveDraft(saved.copy(latitude = 34.5, longitude = 76.1, altitude = 2700.0))
        val updated = repository.getById(rowId)!!
        assertTrue(updated.hasLocation)
        assertEquals(SurveyEntity.STATUS_SAVED, updated.status)
        assertEquals("Sankoo", updated.village)
    }
}
