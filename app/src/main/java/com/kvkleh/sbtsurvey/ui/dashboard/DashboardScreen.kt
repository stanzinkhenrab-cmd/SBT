package com.kvkleh.sbtsurvey.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.export.ShareLauncher
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.components.rememberFileSaver
import com.kvkleh.sbtsurvey.ui.dashboardViewModel

/**
 * Home screen: a prominent New Survey action, the list of saved records, and the
 * three-dot menu holding Survey Map / Export Data / About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNewSurvey: (Long) -> Unit,
    onOpenSurvey: (Long) -> Unit,
    onEditSurvey: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val viewModel = dashboardViewModel()
    val surveys by viewModel.surveys.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fileSaver = rememberFileSaver { savedName, error ->
        when {
            error != null -> viewModel.report(error)
            savedName != null -> viewModel.report("Saved $savedName")
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(uiState.pendingShare) {
        uiState.pendingShare?.let { result ->
            runCatching { ShareLauncher.share(context, result) }
                .onFailure { snackbarHostState.showSnackbar("No app available to share this file.") }
            viewModel.consumeShare()
        }
    }

    LaunchedEffect(uiState.pendingSave) {
        uiState.pendingSave?.let { result ->
            fileSaver.save(result.file)
            viewModel.consumeSave()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Seabuckthorn Field Survey",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        Text(
                            text = "Krishi Vigyan Kendra – Leh, Ladakh",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Survey Map") },
                                leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenMap()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Data") },
                                leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showExport = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenAbout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SummaryStrip(surveys)
                }

                if (surveys.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
                }

                items(surveys, key = { it.id }) { survey ->
                    SurveyCard(
                        survey = survey,
                        onView = { onOpenSurvey(survey.id) },
                        onEdit = { onEditSurvey(survey.id) },
                        onDelete = { viewModel.askDelete(survey) }
                    )
                }
            }

            Button(
                onClick = { viewModel.startNewSurvey(onNewSurvey) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("New Survey", style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    uiState.pendingDelete?.let { target ->
        DeleteConfirmationDialog(
            survey = target,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    if (showExport) {
        ExportSheet(
            busy = uiState.exporting,
            surveyCount = surveys.size,
            photoCount = surveys.count { it.photoPath != null },
            onDismiss = { showExport = false },
            onChoose = { format, share ->
                viewModel.export(format, thenShare = share)
                showExport = false
            }
        )
    }
}

@Composable
private fun SummaryStrip(surveys: List<SurveyEntity>) {
    val completed = surveys.count { it.status == "completed" }
    val drafts = surveys.size - completed
    val withGps = surveys.count { it.hasGps }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(completed.toString(), "Completed")
            SummaryItem(drafts.toString(), "Drafts")
            SummaryItem(withGps.toString(), "With GPS")
        }
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "No surveys saved yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap New Survey to record your first Seabuckthorn observation.\n" +
                "Everything is stored on this device and works without internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    survey: SurveyEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${survey.surveyId}?") },
        text = {
            Text(
                "This will permanently remove the survey record" +
                    (if (survey.photoPath != null) " and its photograph" else "") +
                    " from this device.\n\n" +
                    "${survey.village.ifBlank { "Village not entered" }} · ${Fmt.date(survey.date)}\n" +
                    "Deleted records cannot be recovered."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Delete") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
