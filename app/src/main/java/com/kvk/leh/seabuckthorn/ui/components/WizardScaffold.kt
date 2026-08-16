package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared chrome for every step of the survey wizard: title, step progress bar, and
 * Back/Next (or Save on the last step) buttons pinned to the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScaffold(
    title: String,
    stepIndex: Int,
    stepCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "Next",
    nextEnabled: Boolean = true,
    onClose: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onClose != null) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Close")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                LinearProgressIndicator(
                    progress = { (stepIndex + 1).toFloat() / stepCount },
                    modifier = Modifier.fillMaxWidth().size(4.dp)
                )
                Text(
                    text = "Step ${stepIndex + 1} of $stepCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp)
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                }
                Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f)) {
                    Text(nextLabel)
                }
            }
        }
    ) { padding ->
        content(Modifier.padding(padding))
    }
}
