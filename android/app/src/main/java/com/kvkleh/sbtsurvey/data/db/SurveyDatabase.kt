package com.kvkleh.sbtsurvey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SurveyEntity::class, IdCounterEntity::class],
    version = 2,
    exportSchema = true
)
abstract class SurveyDatabase : RoomDatabase() {

    abstract fun surveyDao(): SurveyDao

    companion object {
        private const val NAME = "sbt_survey.db"

        /**
         * Adds the fruit shape column. Written as a migration rather than a
         * destructive fallback so that surveys already collected on version 1
         * survive the update untouched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE surveys ADD COLUMN fruitShape TEXT")
            }
        }

        @Volatile
        private var instance: SurveyDatabase? = null

        fun get(context: Context): SurveyDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): SurveyDatabase =
            Room.databaseBuilder(context, SurveyDatabase::class.java, NAME)
                // Field research data is irreplaceable: never drop tables on an
                // unexpected schema. A migration must be written instead.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
