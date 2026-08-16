package com.kvk.leh.seabuckthorn.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.preferences.AppPreferences
import com.kvk.leh.seabuckthorn.data.preferences.SurveyorProfile
import kotlinx.coroutines.launch

class OnboardingViewModel(private val appPreferences: AppPreferences) : ViewModel() {

    fun saveProfileAndComplete(profile: SurveyorProfile, onDone: () -> Unit) {
        viewModelScope.launch {
            appPreferences.saveSurveyorProfile(profile)
            appPreferences.setOnboardingCompleted(true)
            onDone()
        }
    }
}
