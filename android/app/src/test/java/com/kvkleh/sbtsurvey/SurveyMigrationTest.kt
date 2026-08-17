package com.kvkleh.sbtsurvey

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kvkleh.sbtsurvey.data.db.SurveyDatabase
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adding the fruit shape column must not cost anybody a season of field data.
 *
 * This builds a version 1 database by hand — the schema surveyors already have
 * records in — then opens it with the current Room definition and checks that the
 * migration runs, Room accepts the resulting schema, and the old record is intact
 * with the new column simply empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SurveyMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun version1RecordsSurviveTheFruitShapeMigration() {
        val name = "migration_test.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()

        createVersion1Database(file.absolutePath)

        val database = Room.databaseBuilder(context, SurveyDatabase::class.java, name)
            .addMigrations(SurveyDatabase.MIGRATION_1_2)
            .build()

        try {
            val survey = runBlocking { database.surveyDao().getBySurveyId("SBT-2026-0001") }
            assertNotNull("the version 1 record should survive the migration", survey)
            requireNotNull(survey)

            assertEquals("Stanzin Khenrab", survey.surveyorName)
            assertEquals("Sakti", survey.village)
            assertEquals("Leh", survey.district)
            assertEquals(SurveyEntity.STATUS_SAVED, survey.status)
            assertEquals("Hardwood", survey.shrubType)
            assertEquals(2.4, survey.plantHeight)
            assertEquals("Ripe", survey.maturityStage)
            assertEquals(11.2, survey.tssBrix)
            assertEquals("SBT-2026-0001.jpg", survey.photoFileName)
            assertEquals(34.1526, survey.latitude)

            // The new column exists and is simply empty for older records.
            assertNull(survey.fruitShape)

            // And the migrated database still accepts new work.
            val created = runBlocking {
                val id = database.surveyDao()
                    .insertWithNewSurveyId(SurveyEntity(), System.currentTimeMillis())
                database.surveyDao().getById(id)
            }
            assertNotNull(created)
            runBlocking {
                database.surveyDao().update(requireNotNull(created).copy(fruitShape = "Oval"))
                assertEquals("Oval", database.surveyDao().getById(created!!.id)?.fruitShape)
            }
        } finally {
            database.close()
            file.delete()
        }
    }

    /**
     * The schema exactly as version 1 of the app created it: the same columns,
     * types, null-ability and indices, without `fruitShape`.
     */
    private fun createVersion1Database(path: String) {
        val legacy = SQLiteDatabase.openOrCreateDatabase(path, null)
        legacy.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `surveys` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `surveyId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `savedAt` INTEGER,
                `surveyorName` TEXT NOT NULL,
                `designation` TEXT NOT NULL,
                `organization` TEXT NOT NULL,
                `district` TEXT NOT NULL,
                `block` TEXT NOT NULL,
                `village` TEXT NOT NULL,
                `site` TEXT NOT NULL,
                `photoPath` TEXT,
                `photoFileName` TEXT,
                `latitude` REAL,
                `longitude` REAL,
                `altitude` REAL,
                `accuracyM` REAL,
                `locationCapturedAt` INTEGER,
                `shrubType` TEXT,
                `plantHeight` REAL,
                `plantHeightUnit` TEXT NOT NULL,
                `maturityStage` TEXT,
                `harvestDate` INTEGER,
                `berryDiameterMm` REAL,
                `tssBrix` REAL,
                `easeOfHarvest` TEXT
            )
            """.trimIndent()
        )
        legacy.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_surveys_surveyId` ON `surveys` (`surveyId`)"
        )
        legacy.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_surveys_status` ON `surveys` (`status`)"
        )
        legacy.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `id_counter` (
                `year` INTEGER NOT NULL,
                `lastSequence` INTEGER NOT NULL,
                PRIMARY KEY(`year`)
            )
            """.trimIndent()
        )
        legacy.execSQL(
            """
            INSERT INTO surveys (
                surveyId, status, createdAt, updatedAt, savedAt,
                surveyorName, designation, organization,
                district, block, village, site,
                photoPath, photoFileName,
                latitude, longitude, altitude, accuracyM, locationCapturedAt,
                shrubType, plantHeight, plantHeightUnit,
                maturityStage, harvestDate, berryDiameterMm, tssBrix, easeOfHarvest
            ) VALUES (
                'SBT-2026-0001', 'SAVED', 1767225000000, 1767225000000, 1767225000000,
                'Stanzin Khenrab', 'SMS Horticulture', 'KVK Leh',
                'Leh', 'Kharu', 'Sakti', 'Riverbank plantation',
                '/data/photos/SBT-2026-0001.jpg', 'SBT-2026-0001.jpg',
                34.1526, 77.5771, 3524.0, 4.0, 1767225000000,
                'Hardwood', 2.4, 'm',
                'Ripe', 1767225000000, 6.4, 11.2, 'Medium'
            )
            """.trimIndent()
        )
        legacy.execSQL("INSERT INTO id_counter (year, lastSequence) VALUES (2026, 1)")
        legacy.version = 1
        legacy.close()
    }
}
