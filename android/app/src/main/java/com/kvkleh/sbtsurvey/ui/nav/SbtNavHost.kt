package com.kvkleh.sbtsurvey.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kvkleh.sbtsurvey.ui.about.AboutScreen
import com.kvkleh.sbtsurvey.ui.camera.CameraCaptureScreen
import com.kvkleh.sbtsurvey.ui.detail.SurveyDetailScreen
import com.kvkleh.sbtsurvey.ui.export.ExportScreen
import com.kvkleh.sbtsurvey.ui.form.SurveyFormScreen
import com.kvkleh.sbtsurvey.ui.form.SurveyorScreen
import com.kvkleh.sbtsurvey.ui.home.WelcomeScreen
import com.kvkleh.sbtsurvey.ui.list.SavedSurveysScreen
import com.kvkleh.sbtsurvey.ui.map.SurveyMapScreen
import com.kvkleh.sbtsurvey.ui.review.ReviewScreen
import com.kvkleh.sbtsurvey.ui.review.SavedConfirmationScreen
import com.kvkleh.sbtsurvey.ui.share.ShareDataScreen

/**
 * Welcome → Surveyor → Form → Review → Saved, plus the three menu destinations.
 * Deliberately shallow: a surveyor recording many plants should never have to
 * walk back through a stack of screens.
 */
object Routes {
    const val WELCOME = "welcome"
    const val SURVEYOR = "surveyor/{rowId}"
    const val FORM = "form/{rowId}"
    const val CAMERA = "camera/{rowId}"
    const val REVIEW = "review/{rowId}"
    const val SAVED_CONFIRMATION = "saved/{rowId}"
    const val DETAIL = "detail/{rowId}"
    const val LIST = "surveys"
    const val MAP = "map"
    const val EXPORT = "export"
    const val SHARE = "share"
    const val ABOUT = "about"

    const val ARG_ROW_ID = "rowId"

    fun surveyor(rowId: Long) = "surveyor/$rowId"
    fun form(rowId: Long) = "form/$rowId"
    fun camera(rowId: Long) = "camera/$rowId"
    fun review(rowId: Long) = "review/$rowId"
    fun savedConfirmation(rowId: Long) = "saved/$rowId"
    fun detail(rowId: Long) = "detail/$rowId"
}

@Composable
fun SbtNavHost(navController: NavHostController = rememberNavController()) {

    fun rowIdArgument() = listOf(
        navArgument(Routes.ARG_ROW_ID) { type = NavType.LongType }
    )

    NavHost(navController = navController, startDestination = Routes.WELCOME) {

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onStartSurvey = { rowId -> navController.navigate(Routes.surveyor(rowId)) },
                onOpenSavedSurveys = { navController.navigate(Routes.LIST) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenExport = { navController.navigate(Routes.EXPORT) },
                onOpenShare = { navController.navigate(Routes.SHARE) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.SURVEYOR, arguments = rowIdArgument()) { entry ->
            val rowId = entry.arguments?.getLong(Routes.ARG_ROW_ID) ?: 0L
            SurveyorScreen(
                surveyRowId = rowId,
                onContinue = { navController.navigate(Routes.form(rowId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FORM, arguments = rowIdArgument()) { entry ->
            val rowId = entry.arguments?.getLong(Routes.ARG_ROW_ID) ?: 0L
            SurveyFormScreen(
                surveyRowId = rowId,
                onReview = { navController.navigate(Routes.review(rowId)) },
                onTakePhoto = { navController.navigate(Routes.camera(rowId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CAMERA, arguments = rowIdArgument()) { entry ->
            val rowId = entry.arguments?.getLong(Routes.ARG_ROW_ID) ?: 0L
            CameraCaptureScreen(
                surveyRowId = rowId,
                onPhotoSaved = { _, _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.REVIEW, arguments = rowIdArgument()) { entry ->
            val rowId = entry.arguments?.getLong(Routes.ARG_ROW_ID) ?: 0L
            ReviewScreen(
                surveyRowId = rowId,
                onSaved = { savedId ->
                    navController.navigate(Routes.savedConfirmation(savedId)) {
                        // The completed record is finished business; going back from
                        // the confirmation must not re-open the form.
                        popUpTo(Routes.WELCOME) { inclusive = false }
                    }
                },
                onEdit = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SAVED_CONFIRMATION, arguments = rowIdArgument()) { entry ->
            val rowId = entry.arguments?.getLong(Routes.ARG_ROW_ID) ?: 0L
            SavedConfirmationScreen(
                surveyRowId = rowId,
                onNewSurvey = { newId ->
                    navController.navigate(Routes.surveyor(newId)) {
                        popUpTo(Routes.WELCOME) { inclusive = false }
                    }
                },
                onViewSurveys = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.WELCOME) { inclusive = false }
                    }
                },
                onHome = {
                    navController.popBackStack(Routes.WELCOME, inclusive = false)
                }
            )
        }

        composable(Routes.LIST) {
            SavedSurveysScreen(
                onOpenSurvey = { rowId -> navController.navigate(Routes.detail(rowId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DETAIL, arguments = rowIdArgument()) { entry ->
            val rowId = entry.arguments?.getLong(Routes.ARG_ROW_ID) ?: 0L
            SurveyDetailScreen(
                surveyRowId = rowId,
                onEdit = { id -> navController.navigate(Routes.form(id)) },
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MAP) {
            SurveyMapScreen(
                onOpenSurvey = { rowId -> navController.navigate(Routes.detail(rowId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.EXPORT) {
            ExportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SHARE) {
            ShareDataScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
