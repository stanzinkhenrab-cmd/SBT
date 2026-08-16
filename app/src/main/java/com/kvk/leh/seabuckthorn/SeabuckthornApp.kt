package com.kvk.leh.seabuckthorn

import android.app.Application
import com.kvk.leh.seabuckthorn.data.local.AppDatabase
import com.kvk.leh.seabuckthorn.data.preferences.AppPreferences
import com.kvk.leh.seabuckthorn.data.repository.SurveyRepository
import com.kvk.leh.seabuckthorn.domain.SurveyCodeGenerator
import com.kvk.leh.seabuckthorn.location.LocationTracker

/**
 * Application-scoped dependency container. The app is small enough that a manual container
 * (rather than a DI framework) keeps things simple and dependency-free while still giving every
 * screen a single shared instance of the database, repository and preferences.
 */
class SeabuckthornApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val surveyRepository: SurveyRepository by lazy { SurveyRepository(database) }
    val appPreferences: AppPreferences by lazy { AppPreferences(this) }
    val surveyCodeGenerator: SurveyCodeGenerator by lazy { SurveyCodeGenerator(database.surveyDao()) }
    val locationTracker: LocationTracker by lazy { LocationTracker(this) }
}
