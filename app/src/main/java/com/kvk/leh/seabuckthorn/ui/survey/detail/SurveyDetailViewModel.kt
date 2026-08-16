package com.kvk.leh.seabuckthorn.ui.survey.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.repository.SurveyRepository
import com.kvk.leh.seabuckthorn.domain.model.SurveyRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SurveyDetailViewModel(private val repository: SurveyRepository) : ViewModel() {

    private val _record = MutableStateFlow<SurveyRecord?>(null)
    val record: StateFlow<SurveyRecord?> = _record.asStateFlow()

    fun load(surveyId: String) {
        viewModelScope.launch {
            _record.value = repository.getSurveyRecord(surveyId)
        }
    }

    fun delete(surveyId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSurvey(surveyId)
            onDeleted()
        }
    }
}
