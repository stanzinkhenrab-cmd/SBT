package com.kvkleh.sbtsurvey.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import com.kvkleh.sbtsurvey.export.ExportFormat
import com.kvkleh.sbtsurvey.export.ExportManager
import com.kvkleh.sbtsurvey.export.ExportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val exporting: Boolean = false,
    /** Populated after a successful export so the UI can offer "Share" straight away. */
    val lastExport: ExportResult? = null,
    /** Set when the surveyor asked to share; consumed by the screen to open the Sharesheet. */
    val pendingShare: ExportResult? = null,
    /** Set when the surveyor asked to save; consumed by the screen to open the file picker. */
    val pendingSave: ExportResult? = null,
    val message: String? = null,
    val pendingDelete: SurveyEntity? = null
)

class DashboardViewModel(
    private val repository: SurveyRepository,
    private val exportManager: ExportManager
) : ViewModel() {

    val surveys: StateFlow<List<SurveyEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Creates a draft and hands its row id back so the caller can navigate to the form. */
    fun startNewSurvey(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(repository.createDraft())
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = "A new survey could not be started. Please try again.")
                }
            }
        }
    }

    /** Resumes the most recently edited unfinished survey, if there is one. */
    fun resumeLatestDraft(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            repository.latestDraft()?.let { onReady(it.id) }
        }
    }

    fun askDelete(survey: SurveyEntity) = _uiState.update { it.copy(pendingDelete = survey) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            try {
                repository.delete(target)
                _uiState.update {
                    it.copy(pendingDelete = null, message = "${target.surveyId} deleted")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(pendingDelete = null, message = "That survey could not be deleted.")
                }
            }
        }
    }

    /**
     * Produces the export file, then either hands it to the Sharesheet or to the system
     * file picker. Both paths generate exactly the same file.
     */
    fun export(format: ExportFormat, thenShare: Boolean) {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            try {
                val result = exportManager.export(format)
                _uiState.update {
                    it.copy(
                        exporting = false,
                        lastExport = result,
                        pendingShare = if (thenShare) result else null,
                        pendingSave = if (thenShare) null else result
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exporting = false,
                        message = e.message ?: "Export failed. Please try again."
                    )
                }
            }
        }
    }

    fun consumeShare() = _uiState.update { it.copy(pendingShare = null) }

    fun consumeSave() = _uiState.update { it.copy(pendingSave = null) }

    fun report(message: String) = _uiState.update { it.copy(message = message) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
