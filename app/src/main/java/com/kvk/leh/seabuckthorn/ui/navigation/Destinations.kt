package com.kvk.leh.seabuckthorn.ui.navigation

/** All navigable routes in the app. Using a sealed object keeps route strings in one place. */
object Destinations {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SURVEY_LIST = "survey_list"
    const val SURVEY_DETAIL = "survey_detail/{surveyId}"
    const val SURVEY_WIZARD = "survey_wizard/{surveyId}"
    const val SURVEY_MAP = "survey_map"
    const val EXPORT = "export"
    const val BACKUP_RESTORE = "backup_restore"
    const val ABOUT = "about"

    const val NEW_SURVEY_ID = "new"

    fun surveyDetail(surveyId: String) = "survey_detail/$surveyId"
    fun surveyWizard(surveyId: String) = "survey_wizard/$surveyId"
}
