package com.kvkleh.sbtsurvey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SurveyEntity::class, IdCounterEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SurveyDatabase : RoomDatabase() {

    abstract fun surveyDao(): SurveyDao

    companion object {
        private const val NAME = "sbt_survey.db"

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
                .build()
    }
}
