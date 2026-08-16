package com.kvk.leh.seabuckthorn.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "seabuckthorn_prefs")

data class SurveyorProfile(
    val name: String = "",
    val designation: String = "",
    val organization: String = "Krishi Vigyan Kendra - Leh, Ladakh"
) {
    val isComplete: Boolean get() = name.isNotBlank()
}

/** Small app-level settings persisted via DataStore: onboarding status and the surveyor profile. */
class AppPreferences(context: Context) {

    private val dataStore = context.dataStore

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SURVEYOR_NAME = stringPreferencesKey("surveyor_name")
        val SURVEYOR_DESIGNATION = stringPreferencesKey("surveyor_designation")
        val SURVEYOR_ORGANIZATION = stringPreferencesKey("surveyor_organization")
    }

    val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val surveyorProfile: Flow<SurveyorProfile> = dataStore.data.map { prefs ->
        SurveyorProfile(
            name = prefs[Keys.SURVEYOR_NAME] ?: "",
            designation = prefs[Keys.SURVEYOR_DESIGNATION] ?: "",
            organization = prefs[Keys.SURVEYOR_ORGANIZATION] ?: "Krishi Vigyan Kendra - Leh, Ladakh"
        )
    }

    suspend fun currentProfile(): SurveyorProfile = surveyorProfile.first()

    suspend fun saveSurveyorProfile(profile: SurveyorProfile) {
        dataStore.edit { prefs ->
            prefs[Keys.SURVEYOR_NAME] = profile.name
            prefs[Keys.SURVEYOR_DESIGNATION] = profile.designation
            prefs[Keys.SURVEYOR_ORGANIZATION] = profile.organization
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = completed }
    }
}
