package com.kvkleh.sbtsurvey

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kvkleh.sbtsurvey.data.local.AppDatabase
import com.kvkleh.sbtsurvey.data.local.SurveyDao
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the two database invariants field data depends on: Survey IDs are unique, and a
 * draft survives being written and read back.
 */
@RunWith(AndroidJUnit4::class)
class SurveyDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: SurveyDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = database.surveyDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun surveyIdsAreUniqueAndSequential() = runBlocking {
        val ids = (1..25).map {
            val rowId = dao.insertWithNewSurveyId(2026, SurveyEntity(surveyId = ""))
            dao.getById(rowId)!!.surveyId
        }

        assertEquals(25, ids.toSet().size)
        assertEquals("SBT-2026-0001", ids.first())
        assertEquals("SBT-2026-0025", ids.last())
    }

    @Test
    fun sequenceSkipsIdsThatAlreadyExist() = runBlocking {
        // Simulates a restored backup where records exist but the counter lags behind.
        dao.insert(SurveyEntity(surveyId = "SBT-2026-0001"))
        dao.insert(SurveyEntity(surveyId = "SBT-2026-0002"))

        val rowId = dao.insertWithNewSurveyId(2026, SurveyEntity(surveyId = ""))

        assertEquals("SBT-2026-0003", dao.getById(rowId)!!.surveyId)
    }

    @Test
    fun draftIsRecoverableAfterWrite() = runBlocking {
        val rowId = dao.insertWithNewSurveyId(
            2026,
            SurveyEntity(surveyId = "", village = "Saboo", status = "draft")
        )
        dao.update(dao.getById(rowId)!!.copy(berryDiameter = 8.5, updatedAt = 42L))

        val draft = dao.latestDraft()
        assertTrue(draft != null)
        assertEquals("Saboo", draft!!.village)
        assertEquals(8.5, draft.berryDiameter!!, 1e-9)
    }
}
