package com.kvkleh.sbtsurvey.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class CameraUiState(
    val surveyId: String = "",
    val village: String = "",
    val ready: Boolean = false
)

/**
 * Knows which survey the photograph belongs to. The image is written to a pending
 * file first and only renamed to `<Survey ID>.jpg` once the camera reports a
 * complete capture, so a cancelled shot cannot leave a broken image behind.
 */
class CameraViewModel : ViewModel() {

    private val repository = Graph.repository
    private val photoStore = Graph.photoStore

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var rowId: Long = 0L

    fun load(id: Long) {
        if (rowId == id && _uiState.value.ready) return
        rowId = id
        viewModelScope.launch {
            val survey = repository.getById(id) ?: return@launch
            _uiState.value = CameraUiState(
                surveyId = survey.surveyId,
                village = survey.village,
                ready = true
            )
        }
    }

    fun pendingFile(): File? {
        val surveyId = _uiState.value.surveyId
        if (surveyId.isBlank()) return null
        photoStore.photoDir
        return photoStore.pendingFileFor(surveyId)
    }

    /** Promotes the pending capture and links it to the record. */
    fun commitCapture(onSaved: (String, String) -> Unit, onFailed: (String) -> Unit) {
        val surveyId = _uiState.value.surveyId
        viewModelScope.launch {
            val file = photoStore.commitPending(surveyId)
            if (file == null) {
                onFailed("The photo could not be saved. Please take it again.")
                return@launch
            }
            repository.attachPhoto(rowId, file.absolutePath, file.name)
            onSaved(file.absolutePath, file.name)
        }
    }

    fun discardPending() {
        val surveyId = _uiState.value.surveyId
        if (surveyId.isNotBlank()) photoStore.discardPending(surveyId)
    }
}
