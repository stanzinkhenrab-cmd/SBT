package com.kvkleh.sbtsurvey.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kvkleh.sbtsurvey.AppContainer
import com.kvkleh.sbtsurvey.SbtApplication
import com.kvkleh.sbtsurvey.ui.dashboard.DashboardViewModel
import com.kvkleh.sbtsurvey.ui.map.SurveyMapViewModel
import com.kvkleh.sbtsurvey.ui.survey.SurveyFormViewModel

/** Resolves the application container from any composable. */
@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current.applicationContext
    return (context as SbtApplication).container
}

private class SimpleFactory(
    private val creator: (AppContainer) -> ViewModel,
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        creator(container) as T
}

@Composable
fun dashboardViewModel(): DashboardViewModel {
    val container = appContainer()
    return viewModel(
        factory = SimpleFactory(
            { DashboardViewModel(it.surveyRepository, it.exportManager) },
            container
        )
    )
}

@Composable
fun surveyFormViewModel(surveyId: Long): SurveyFormViewModel {
    val container = appContainer()
    return viewModel(
        key = "survey-form-$surveyId",
        factory = SimpleFactory(
            { SurveyFormViewModel(surveyId, it.surveyRepository, it.photoStore, it.appContext) },
            container
        )
    )
}

@Composable
fun surveyMapViewModel(): SurveyMapViewModel {
    val container = appContainer()
    return viewModel(
        factory = SimpleFactory(
            { SurveyMapViewModel(it.surveyRepository, it.tileCache, it.appContext) },
            container
        )
    )
}
