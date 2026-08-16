package com.kvkleh.sbtsurvey.ui.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvkleh.sbtsurvey.data.SurveyField
import com.kvkleh.sbtsurvey.data.SurveyOptions
import com.kvkleh.sbtsurvey.ui.components.FormColumn
import com.kvkleh.sbtsurvey.ui.components.SbtDropdownField
import com.kvkleh.sbtsurvey.ui.components.SbtTextField
import com.kvkleh.sbtsurvey.ui.components.SectionCard

/**
 * Step one of the workflow. The details entered here are remembered and pre-filled
 * on every later survey, so they normally only need to be confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyorScreen(
    surveyRowId: Long,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: SurveyFormViewModel = viewModel()
) {
    LaunchedEffect(surveyRowId) { viewModel.load(surveyRowId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val survey = state.survey

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Surveyor Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (survey == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        FormColumn(modifier = Modifier.padding(padding)) {
            SectionCard(
                number = null,
                icon = Icons.Filled.Badge,
                title = "Who is recording this survey",
                subtitle = "Saved for the next survey so it is only entered once"
            ) {
                SbtTextField(
                    value = survey.surveyorName,
                    onValueChange = viewModel::setSurveyorName,
                    label = "Surveyor Name",
                    required = true,
                    errorText = state.errorFor(SurveyField.SURVEYOR_NAME)
                )
                SbtTextField(
                    value = survey.designation,
                    onValueChange = viewModel::setDesignation,
                    label = "Designation",
                    required = true,
                    supportingText = "For example: Subject Matter Specialist (Horticulture)",
                    errorText = state.errorFor(SurveyField.DESIGNATION)
                )
                SbtDropdownField(
                    value = survey.organization,
                    onValueChange = viewModel::setOrganization,
                    label = "Organization",
                    options = SurveyOptions.organizations,
                    required = true,
                    supportingText = "Pick from the list or type another organization",
                    errorText = state.errorFor(SurveyField.ORGANIZATION)
                )
            }

            Text(
                text = "Survey ${survey.surveyId} has been created and is being saved " +
                    "automatically as you type.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    if (viewModel.validateSurveyor()) onContinue()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Continue to Survey Form", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
