package com.kvkleh.sbtsurvey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SurveyEntity::class, SurveySequenceEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun surveyDao(): SurveyDao

    companion object {
        private const val DB_NAME = "sbt_survey.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                // Write-ahead logging keeps writes durable and fast; combined with the
                // per-field autosave this is what makes an interrupted survey recoverable.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Future releases add migrations here. Destructive fallback is deliberately
                // NOT enabled: field data must never be silently dropped by an upgrade.
                .addMigrations(*MIGRATIONS)
                .build()

        /** Schema migrations, oldest first. Empty at version 1. */
        private val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}
