package com.kvk.leh.seabuckthorn.domain

import com.kvk.leh.seabuckthorn.data.local.dao.SurveyDao
import java.time.Year

/**
 * Generates sequential, human-readable survey IDs of the form SBT-<year>-<0001>.
 * The sequence resets each calendar year and is derived from the highest existing code
 * for that year already stored in the database, so it stays correct offline across app restarts.
 */
class SurveyCodeGenerator(private val surveyDao: SurveyDao) {

    suspend fun nextCode(forYear: Int = Year.now().value): String {
        val prefix = "SBT-$forYear-"
        val last = surveyDao.getLastSurveyCodeForPrefix(prefix)
        val nextSequence = if (last != null) {
            val sequencePart = last.removePrefix(prefix).toIntOrNull() ?: 0
            sequencePart + 1
        } else {
            1
        }
        return prefix + nextSequence.toString().padStart(4, '0')
    }
}
