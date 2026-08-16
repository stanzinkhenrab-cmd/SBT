package com.kvkleh.sbtsurvey

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kvkleh.sbtsurvey.data.db.IdCounterEntity
import com.kvkleh.sbtsurvey.data.db.SurveyDao
import com.kvkleh.sbtsurvey.data.db.SurveyDatabase
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The identifier rules are the part of this app that must never fail: a duplicate
 * or reused Survey ID would silently corrupt a season of field data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SurveyDaoTest {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: SurveyDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SurveyDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.surveyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun identifiersIncrementWithoutDuplicates() = runBlocking {
        val now = System.currentTimeMillis()
        val ids = (1..25).map {
            val rowId = dao.insertWithNewSurveyId(SurveyEntity(), now)
            dao.getById(rowId)!!.surveyId
        }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.first().startsWith("SBT-"))
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun concurrentStartsNeverCollide() = runBlocking {
        val now = System.currentTimeMillis()
        val rowIds = withContext(Dispatchers.Default) {
            (1..12).map { async { dao.insertWithNewSurveyId(SurveyEntity(), now) } }.awaitAll()
        }
        val surveyIds = rowIds.map { dao.getById(it)!!.surveyId }
        assertEquals(12, surveyIds.toSet().size)
    }

    @Test
    fun identifierAlreadyInTheTableIsSkipped() = runBlocking {
        val now = System.currentTimeMillis()
        val first = dao.getById(dao.insertWithNewSurveyId(SurveyEntity(), now))!!
        // Simulate a restored database whose counter has fallen behind its records.
        dao.upsertCounter(
            IdCounterEntity(year = first.surveyId.substring(4, 8).toInt(), lastSequence = 0)
        )
        val second = dao.getById(dao.insertWithNewSurveyId(SurveyEntity(), now))!!
        assertTrue(second.surveyId != first.surveyId)
        assertNotNull(dao.getBySurveyId(first.surveyId))
    }

    @Test
    fun draftsAreSeparateFromSavedRecords() = runBlocking {
        val now = System.currentTimeMillis()
        val draftId = dao.insertWithNewSurveyId(SurveyEntity(), now)
        val savedId = dao.insertWithNewSurveyId(SurveyEntity(), now)
        dao.update(dao.getById(savedId)!!.copy(status = SurveyEntity.STATUS_SAVED))

        assertEquals(SurveyEntity.STATUS_DRAFT, dao.getById(draftId)!!.status)
        assertEquals(draftId, dao.getLatestDraft()?.id)
        assertEquals(2, dao.getAllForExport().size)
    }

    @Test
    fun deletingARecordLeavesTheRestIntact() = runBlocking {
        val now = System.currentTimeMillis()
        val first = dao.getById(dao.insertWithNewSurveyId(SurveyEntity(), now))!!
        val second = dao.getById(dao.insertWithNewSurveyId(SurveyEntity(), now))!!
        dao.delete(first)
        assertEquals(1, dao.getAllForExport().size)
        assertNotNull(dao.getBySurveyId(second.surveyId))
        // The counter does not roll back, so a deleted number is never reissued.
        val third = dao.getById(dao.insertWithNewSurveyId(SurveyEntity(), now))!!
        assertTrue(third.surveyId != first.surveyId)
    }
}
