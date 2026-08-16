package com.kvk.leh.seabuckthorn.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(viewModel: ExportViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "Spreadsheet Exports", subtitle = "Suitable for Excel, R, Python, SPSS and GIS import.") {
                Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
                Button(onClick = viewModel::exportXlsx, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Excel (.xlsx, 9 sheets)")
                }
            }
            SectionCard(title = "GPS / GIS Exports", subtitle = "Location points only, for mapping software.") {
                Button(onClick = viewModel::exportKml, modifier = Modifier.fillMaxWidth()) { Text("Export KML") }
                Button(onClick = viewModel::exportGeoJson, modifier = Modifier.fillMaxWidth()) { Text("Export GeoJSON") }
            }

            when (val s = state) {
                is ExportUiState.Running -> {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Text("Generating export…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ExportUiState.Done -> {
                    SectionCard(title = "${s.label} ready") {
                        Text(s.file.name, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", s.file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${s.label}"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text("  Share / Save")
                        }
                    }
                }
                is ExportUiState.Error -> {
                    Text("Export failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                }
                ExportUiState.Idle -> Unit
            }

            Text(
                "Exports are written to this device's app-private storage and shared only when you tap Share — nothing is uploaded automatically.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
