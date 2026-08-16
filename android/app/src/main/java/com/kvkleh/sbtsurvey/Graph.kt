package com.kvkleh.sbtsurvey

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.kvkleh.sbtsurvey.data.db.SurveyDatabase
import com.kvkleh.sbtsurvey.data.export.ExportManager
import com.kvkleh.sbtsurvey.data.location.LocationService
import com.kvkleh.sbtsurvey.data.photo.PhotoStore
import com.kvkleh.sbtsurvey.data.prefs.SurveyPreferences
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository

/**
 * Manual dependency container. The app has one database, one photo folder and one
 * location service; a full DI framework would add build complexity without
 * removing any wiring.
 */
object Graph {

    lateinit var repository: SurveyRepository
        private set

    lateinit var preferences: SurveyPreferences
        private set

    lateinit var photoStore: PhotoStore
        private set

    lateinit var locationService: LocationService
        private set

    lateinit var exportManager: ExportManager
        private set

    var appVersion: String = "1.0.0"
        private set

    /**
     * Scope for writes that must finish even if the screen that started them is
     * being destroyed — the final auto-save when the app is closed mid-form.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (::repository.isInitialized) return
        val appContext = context.applicationContext
        appVersion = readVersionName(appContext)
        val database = SurveyDatabase.get(appContext)
        photoStore = PhotoStore(appContext)
        preferences = SurveyPreferences(appContext)
        repository = SurveyRepository(database.surveyDao(), photoStore, preferences)
        locationService = LocationService(appContext)
        exportManager = ExportManager(appContext, appVersion)
    }

    private fun readVersionName(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"
}
