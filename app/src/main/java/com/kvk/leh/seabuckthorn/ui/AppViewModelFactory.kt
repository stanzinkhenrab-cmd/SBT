package com.kvk.leh.seabuckthorn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kvk.leh.seabuckthorn.SeabuckthornApp
import com.kvk.leh.seabuckthorn.ui.backup.BackupRestoreViewModel
import com.kvk.leh.seabuckthorn.ui.dashboard.DashboardViewModel
import com.kvk.leh.seabuckthorn.ui.export.ExportViewModel
import com.kvk.leh.seabuckthorn.ui.map.SurveyMapViewModel
import com.kvk.leh.seabuckthorn.ui.onboarding.OnboardingViewModel
import com.kvk.leh.seabuckthorn.ui.survey.detail.SurveyDetailViewModel
import com.kvk.leh.seabuckthorn.ui.survey.list.SurveyListViewModel
import com.kvk.leh.seabuckthorn.ui.survey.wizard.SurveyWizardViewModel

/**
 * Single manual [ViewModelProvider.Factory] for the whole app. Kept as one factory (rather than
 * one per screen) since the app is small and every ViewModel's dependencies come from the same
 * [SeabuckthornApp] container.
 */
class AppViewModelFactory(private val app: SeabuckthornApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when (modelClass) {
            OnboardingViewModel::class.java -> OnboardingViewModel(app.appPreferences)
            DashboardViewModel::class.java -> DashboardViewModel(app.surveyRepository)
            SurveyListViewModel::class.java -> SurveyListViewModel(app.surveyRepository, app.surveyCodeGenerator)
            SurveyWizardViewModel::class.java -> SurveyWizardViewModel(
                app.surveyRepository, app.surveyCodeGenerator, app.appPreferences, app.locationTracker
            )
            SurveyDetailViewModel::class.java -> SurveyDetailViewModel(app.surveyRepository)
            SurveyMapViewModel::class.java -> SurveyMapViewModel(app.surveyRepository)
            ExportViewModel::class.java -> ExportViewModel(app, app.surveyRepository)
            BackupRestoreViewModel::class.java -> BackupRestoreViewModel(app, app.database)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return viewModel as T
    }
}
