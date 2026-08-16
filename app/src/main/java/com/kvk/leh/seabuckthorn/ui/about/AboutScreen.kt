package com.kvk.leh.seabuckthorn.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.BuildConfig
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            Text("Seabuckthorn Field Survey", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Text("Ladakh", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Seabuckthorn Phenology, Morphology & Fruit Quality Survey",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionCard(title = "Developed By") {
                Text("Stanzin Khenrab", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Krishi Vigyan Kendra – Leh, Ladakh")
                Text("MIDH-SBM")
            }

            SectionCard(title = "Purpose") {
                Text(
                    "Scientific field data collection for seabuckthorn phenology, morphology, fruit " +
                        "characteristics, quality assessment and geospatial documentation in Ladakh."
                )
            }

            SectionCard(title = "Data Collection Methodology") {
                Text(
                    "Each survey record captures standardized observations of a single shrub — site " +
                        "and land-use context, automatically-tagged GPS location, shrub morphology, fruit " +
                        "phenology and maturity staging, individual berry measurements, fruit quality and " +
                        "juice parameters, environmental context, and categorized photographs. Calculated " +
                        "fields (canopy area, berry statistics, maturity totals) are derived automatically " +
                        "from the raw measurements so downstream analysis starts from a consistent dataset."
                )
            }

            androidx.compose.material3.Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "100% Offline",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "GPS, camera, database, calculations, search and export all work without an internet " +
                            "connection. No survey data is ever uploaded automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
