package com.kvk.leh.seabuckthorn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kvk.leh.seabuckthorn.SeabuckthornApp
import com.kvk.leh.seabuckthorn.ui.AppViewModelFactory
import com.kvk.leh.seabuckthorn.ui.about.AboutScreen
import com.kvk.leh.seabuckthorn.ui.backup.BackupRestoreScreen
import com.kvk.leh.seabuckthorn.ui.backup.BackupRestoreViewModel
import com.kvk.leh.seabuckthorn.ui.dashboard.DashboardScreen
import com.kvk.leh.seabuckthorn.ui.dashboard.DashboardViewModel
import com.kvk.leh.seabuckthorn.ui.export.ExportScreen
import com.kvk.leh.seabuckthorn.ui.export.ExportViewModel
import com.kvk.leh.seabuckthorn.ui.map.SurveyMapScreen
import com.kvk.leh.seabuckthorn.ui.map.SurveyMapViewModel
import com.kvk.leh.seabuckthorn.ui.onboarding.OnboardingScreen
import com.kvk.leh.seabuckthorn.ui.onboarding.OnboardingViewModel
import com.kvk.leh.seabuckthorn.ui.survey.detail.SurveyDetailScreen
import com.kvk.leh.seabuckthorn.ui.survey.detail.SurveyDetailViewModel
import com.kvk.leh.seabuckthorn.ui.survey.list.SurveyListScreen
import com.kvk.leh.seabuckthorn.ui.survey.list.SurveyListViewModel
import com.kvk.leh.seabuckthorn.ui.survey.wizard.SurveyWizardScreen
import com.kvk.leh.seabuckthorn.ui.survey.wizard.SurveyWizardViewModel

@Composable
fun SeabuckthornNavGraph(startDestination: String) {
    val app = LocalContext.current.applicationContext as SeabuckthornApp
    val factory = remember { AppViewModelFactory(app) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destinations.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(factory = factory)
            OnboardingScreen(vm) {
                navController.navigate(Destinations.DASHBOARD) {
                    popUpTo(Destinations.ONBOARDING) { inclusive = true }
                }
            }
        }

        composable(Destinations.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = vm,
                onNewSurvey = { navController.navigate(Destinations.surveyWizard(Destinations.NEW_SURVEY_ID)) },
                onOpenSurveyList = { navController.navigate(Destinations.SURVEY_LIST) },
                onOpenMap = { navController.navigate(Destinations.SURVEY_MAP) },
                onOpenExport = { navController.navigate(Destinations.EXPORT) },
                onOpenBackup = { navController.navigate(Destinations.BACKUP_RESTORE) },
                onOpenAbout = { navController.navigate(Destinations.ABOUT) }
            )
        }

        composable(Destinations.SURVEY_LIST) {
            val vm: SurveyListViewModel = viewModel(factory = factory)
            SurveyListScreen(
                viewModel = vm,
                onOpenSurvey = { id -> navController.navigate(Destinations.surveyDetail(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Destinations.SURVEY_DETAIL,
            arguments = listOf(navArgument("surveyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyId = backStackEntry.arguments?.getString("surveyId").orEmpty()
            val vm: SurveyDetailViewModel = viewModel(factory = factory)
            SurveyDetailScreen(
                viewModel = vm,
                surveyId = surveyId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Destinations.surveyWizard(id)) },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(
            Destinations.SURVEY_WIZARD,
            arguments = listOf(navArgument("surveyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyId = backStackEntry.arguments?.getString("surveyId").orEmpty()
            val vm: SurveyWizardViewModel = viewModel(factory = factory)
            SurveyWizardScreen(
                viewModel = vm,
                surveyId = surveyId,
                onSaved = {
                    navController.navigate(Destinations.DASHBOARD) {
                        popUpTo(Destinations.DASHBOARD) { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack() }
            )
        }

        composable(Destinations.SURVEY_MAP) {
            val vm: SurveyMapViewModel = viewModel(factory = factory)
            SurveyMapScreen(
                viewModel = vm,
                onOpenSurvey = { id -> navController.navigate(Destinations.surveyDetail(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.EXPORT) {
            val vm: ExportViewModel = viewModel(factory = factory)
            ExportScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Destinations.BACKUP_RESTORE) {
            val vm: BackupRestoreViewModel = viewModel(factory = factory)
            BackupRestoreScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Destinations.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
