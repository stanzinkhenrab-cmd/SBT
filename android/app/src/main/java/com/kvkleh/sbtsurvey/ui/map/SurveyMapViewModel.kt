package com.kvkleh.sbtsurvey.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SurveyMapUiState(
    val loading: Boolean = true,
    val points: List<SurveyEntity> = emptyList(),
    val withoutLocation: Int = 0,
    val selected: SurveyEntity? = null,
    val useTileMap: Boolean = true
)

class SurveyMapViewModel : ViewModel() {

    private val repository = Graph.repository

    private val _uiState = MutableStateFlow(SurveyMapUiState())
    val uiState: StateFlow<SurveyMapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSaved().collect { surveys ->
                val located = surveys.filter { it.hasLocation }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    points = located,
                    withoutLocation = surveys.size - located.size,
                    selected = _uiState.value.selected?.let { previous ->
                        located.firstOrNull { it.id == previous.id }
                    }
                )
            }
        }
    }

    fun select(survey: SurveyEntity?) {
        _uiState.value = _uiState.value.copy(selected = survey)
    }

    fun setUseTileMap(useTileMap: Boolean) {
        _uiState.value = _uiState.value.copy(useTileMap = useTileMap)
    }
}
