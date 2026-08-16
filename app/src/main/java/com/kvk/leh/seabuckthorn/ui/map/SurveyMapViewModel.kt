package com.kvk.leh.seabuckthorn.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import com.kvk.leh.seabuckthorn.data.repository.SurveyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SurveyMapViewModel(repository: SurveyRepository) : ViewModel() {
    val surveysWithGps: StateFlow<List<SurveyEntity>> =
        repository.observeSurveysWithGps()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
