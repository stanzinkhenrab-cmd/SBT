package com.kvkleh.sbtsurvey.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import com.kvkleh.sbtsurvey.data.SurveyValidator
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    val survey: SurveyEntity? = null,
    val warnings: List<String> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val errorMessage: String? = null
)

class ReviewViewModel : ViewModel() {

    private val repository = Graph.repository

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var rowId: Long = 0L

    /**
     * Observes the record rather than reading it once: the form's final auto-save
     * may still be in flight when this screen opens, and the review must show what
     * is actually on disk.
     */
    fun load(id: Long) {
        if (rowId == id && !_uiState.value.loading) return
        rowId = id
        viewModelScope.launch {
            repository.observeById(id).collect { survey ->
                val validation = survey?.let { SurveyValidator.validate(it) }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    survey = survey,
                    warnings = validation?.warnings.orEmpty(),
                    errors = validation?.errors.orEmpty()
                )
            }
        }
    }

    /** Removes the record and its photo. Only reached from a confirmation dialog. */
    fun delete(onDeleted: () -> Unit) {
        val survey = _uiState.value.survey ?: return
        viewModelScope.launch {
            repository.delete(survey)
            onDeleted()
        }
    }

    /** Writes the record as a completed survey. */
    fun save(onSaved: (SurveyEntity) -> Unit) {
        val survey = _uiState.value.survey ?: return
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true, errorMessage = null)
        viewModelScope.launch {
            val result = runCatching { repository.commit(survey) }
            result.fold(
                onSuccess = { saved ->
                    _uiState.value = _uiState.value.copy(saving = false, survey = saved)
                    onSaved(saved)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        errorMessage = "The survey could not be saved. It is still stored as a draft."
                    )
                }
            )
        }
    }
}
