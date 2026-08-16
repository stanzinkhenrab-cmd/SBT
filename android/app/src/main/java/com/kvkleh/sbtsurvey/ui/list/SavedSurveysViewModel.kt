package com.kvkleh.sbtsurvey.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedSurveysUiState(
    val loading: Boolean = true,
    val query: String = "",
    val allSurveys: List<SurveyEntity> = emptyList(),
    val pendingDelete: SurveyEntity? = null
) {
    /** Newest first, then filtered by the search box. */
    val visibleSurveys: List<SurveyEntity>
        get() {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return allSurveys
            return allSurveys.filter { it.matches(trimmed) }
        }

    val totalCount: Int get() = allSurveys.size
}

private fun SurveyEntity.matches(query: String): Boolean {
    val needle = query.lowercase()
    return surveyId.lowercase().contains(needle) ||
        village.lowercase().contains(needle) ||
        district.lowercase().contains(needle) ||
        block.lowercase().contains(needle) ||
        site.lowercase().contains(needle) ||
        surveyorName.lowercase().contains(needle)
}

class SavedSurveysViewModel : ViewModel() {

    private val repository = Graph.repository

    private val _uiState = MutableStateFlow(SavedSurveysUiState())
    val uiState: StateFlow<SavedSurveysUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSaved().collect { surveys ->
                _uiState.value = _uiState.value.copy(loading = false, allSurveys = surveys)
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun askToDelete(survey: SurveyEntity) {
        _uiState.value = _uiState.value.copy(pendingDelete = survey)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null)
    }

    /** Only ever reached through the confirmation dialog. */
    fun confirmDelete() {
        val survey = _uiState.value.pendingDelete ?: return
        _uiState.value = _uiState.value.copy(pendingDelete = null)
        viewModelScope.launch { repository.delete(survey) }
    }
}
