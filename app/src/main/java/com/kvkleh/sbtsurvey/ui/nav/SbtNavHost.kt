package com.kvkleh.sbtsurvey.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kvkleh.sbtsurvey.ui.about.AboutScreen
import com.kvkleh.sbtsurvey.ui.dashboard.DashboardScreen
import com.kvkleh.sbtsurvey.ui.detail.SurveyDetailScreen
import com.kvkleh.sbtsurvey.ui.map.SurveyMapScreen
import com.kvkleh.sbtsurvey.ui.survey.SurveyCompleteScreen
import com.kvkleh.sbtsurvey.ui.survey.SurveyFormScreen
import com.kvkleh.sbtsurvey.ui.welcome.WelcomeScreen

/**
 * Navigation graph.
 *
 * Deliberately flat and small: Home → New Survey → Form → Save & Finish → Survey List,
 * with the map, about and detail screens hanging off it.
 */
object Routes {
    const val WELCOME = "welcome"
    const val DASHBOARD = "dashboard"
    const val FORM = "form/{surveyId}"
    const val COMPLETE = "complete/{surveyId}"
    const val DETAIL = "detail/{surveyId}"
    const val MAP = "map"
    const val ABOUT = "about"

    fun form(surveyRowId: Long) = "form/$surveyRowId"
    fun complete(surveyRowId: Long) = "complete/$surveyRowId"
    fun detail(surveyRowId: Long) = "detail/$surveyRowId"
}

private const val ARG_SURVEY_ID = "surveyId"

@Composable
fun SbtNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.WELCOME) {

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNewSurvey = { navController.navigate(Routes.form(it)) },
                onOpenSurvey = { navController.navigate(Routes.detail(it)) },
                onEditSurvey = { navController.navigate(Routes.form(it)) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(
            route = Routes.FORM,
            arguments = listOf(navArgument(ARG_SURVEY_ID) { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_SURVEY_ID) ?: 0L
            SurveyFormScreen(
                surveyRowId = id,
                onBack = { navController.popBackStack() },
                onFinished = { finishedId ->
                    navController.navigate(Routes.complete(finishedId)) {
                        popUpTo(Routes.DASHBOARD) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.COMPLETE,
            arguments = listOf(navArgument(ARG_SURVEY_ID) { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_SURVEY_ID) ?: 0L
            SurveyCompleteScreen(
                surveyRowId = id,
                onBackToList = {
                    navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                },
                onViewSurvey = { surveyRowId ->
                    navController.navigate(Routes.detail(surveyRowId)) {
                        popUpTo(Routes.DASHBOARD) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(ARG_SURVEY_ID) { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_SURVEY_ID) ?: 0L
            SurveyDetailScreen(
                surveyRowId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.form(it)) }
            )
        }

        composable(Routes.MAP) {
            SurveyMapScreen(
                onBack = { navController.popBackStack() },
                onOpenSurvey = { navController.navigate(Routes.detail(it)) }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
