package com.kvkleh.sbtsurvey

import android.app.Application
import android.content.Context
import com.kvkleh.sbtsurvey.data.local.AppDatabase
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import com.kvkleh.sbtsurvey.export.ExportManager
import com.kvkleh.sbtsurvey.map.TileCache
import com.kvkleh.sbtsurvey.photo.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-wide object graph.
 *
 * Dependencies are wired by hand rather than with a DI framework: the graph is small,
 * entirely local, and keeping it explicit makes the data path (Room -> repository -> UI)
 * obvious to anyone maintaining the app later.
 */
class SbtApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // A backup of the database is taken on every cold start, before the surveyor can
        // change anything, so a corrupted or accidentally cleared database still leaves a
        // recent copy of the field data on the device.
        container.applicationScope.launch {
            container.surveyRepository.backupDatabase()
        }
    }
}

class AppContainer(application: Application) {

    val appContext: Context = application.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase = AppDatabase.get(application)

    val photoStore: PhotoStore = PhotoStore(application)

    val surveyRepository: SurveyRepository =
        SurveyRepository(database.surveyDao(), photoStore, application)

    val exportManager: ExportManager = ExportManager(application, surveyRepository)

    val tileCache: TileCache = TileCache(application, applicationScope)
}
