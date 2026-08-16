package com.kvkleh.sbtsurvey.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val savedCount: Int = 0,
    val draft: SurveyEntity? = null,
    val surveyorName: String = "",
    val busy: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val repository = Graph.repository
    private val preferences = Graph.preferences

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSavedCount().collect { count ->
                _uiState.value = _uiState.value.copy(savedCount = count)
            }
        }
        viewModelScope.launch {
            repository.observeDraft().collect { draft ->
                _uiState.value = _uiState.value.copy(draft = draft)
            }
        }
        viewModelScope.launch {
            preferences.profile.collect { profile ->
                _uiState.value = _uiState.value.copy(surveyorName = profile.name)
            }
        }
    }

    /**
     * Opens the next survey. An unfinished draft is resumed instead of starting a
     * new record, so a form left open on a previous outing is never lost and no
     * survey number is wasted.
     */
    fun startOrResumeSurvey(onReady: (Long) -> Unit) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true)
        viewModelScope.launch {
            val existing = repository.getLatestDraft()
            val id = existing?.id ?: repository.startDraft(
                profile = preferences.profile.value,
                lastLocation = preferences.lastLocation.value
            )
            _uiState.value = _uiState.value.copy(busy = false)
            onReady(id)
        }
    }

    /** Starts a brand new record even when a draft exists. */
    fun startNewSurvey(onReady: (Long) -> Unit) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true)
        viewModelScope.launch {
            val id = repository.startDraft(
                profile = preferences.profile.value,
                lastLocation = preferences.lastLocation.value
            )
            _uiState.value = _uiState.value.copy(busy = false)
            onReady(id)
        }
    }

    fun discardDraft() {
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch { repository.delete(draft) }
    }
}
