package com.kvk.leh.seabuckthorn.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kvk.leh.seabuckthorn.data.local.dao.BerryMeasurementDao
import com.kvk.leh.seabuckthorn.data.local.dao.EnvironmentDao
import com.kvk.leh.seabuckthorn.data.local.dao.FruitQualityDao
import com.kvk.leh.seabuckthorn.data.local.dao.PhenologyDao
import com.kvk.leh.seabuckthorn.data.local.dao.PhotoDao
import com.kvk.leh.seabuckthorn.data.local.dao.ShrubDao
import com.kvk.leh.seabuckthorn.data.local.dao.SurveyDao
import com.kvk.leh.seabuckthorn.data.local.entity.BerryMeasurementEntity
import com.kvk.leh.seabuckthorn.data.local.entity.EnvironmentEntity
import com.kvk.leh.seabuckthorn.data.local.entity.FruitQualityEntity
import com.kvk.leh.seabuckthorn.data.local.entity.PhenologyEntity
import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import com.kvk.leh.seabuckthorn.data.local.entity.ShrubEntity
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity

/**
 * The app's single offline data store. All survey data lives here permanently on-device;
 * nothing is written unless the user explicitly saves it, and nothing ever leaves the device
 * automatically (see BackupManager for the only supported export/backup paths).
 */
@Database(
    entities = [
        SurveyEntity::class,
        ShrubEntity::class,
        PhenologyEntity::class,
        BerryMeasurementEntity::class,
        FruitQualityEntity::class,
        EnvironmentEntity::class,
        PhotoEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun surveyDao(): SurveyDao
    abstract fun shrubDao(): ShrubDao
    abstract fun phenologyDao(): PhenologyDao
    abstract fun berryMeasurementDao(): BerryMeasurementDao
    abstract fun fruitQualityDao(): FruitQualityDao
    abstract fun environmentDao(): EnvironmentDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val DATABASE_NAME = "seabuckthorn_survey.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }

        /**
         * Closes and forgets the current instance so the underlying .db file can be safely
         * overwritten during a restore. The app process must be restarted afterwards — any
         * component that already holds the old instance (or DAOs/repositories derived from it)
         * would otherwise keep querying a closed connection.
         */
        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
