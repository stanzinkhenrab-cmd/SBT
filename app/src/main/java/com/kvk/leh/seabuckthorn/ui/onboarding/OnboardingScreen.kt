package com.kvk.leh.seabuckthorn.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kvk.leh.seabuckthorn.data.preferences.SurveyorProfile
import com.kvk.leh.seabuckthorn.ui.components.LabeledTextField

/**
 * First-launch flow: (1) create a surveyor profile, (2) explain and request location permission,
 * (3) explain and request camera permission. Runs once — [OnboardingViewModel] persists the
 * "onboarding complete" flag so this never shows again after permissions have been decided.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("Krishi Vigyan Kendra - Leh, Ladakh") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { step = 2 }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.saveProfileAndComplete(
            SurveyorProfile(name = name.trim(), designation = designation.trim(), organization = organization.trim()),
            onDone = onFinished
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            when (step) {
                0 -> ProfileStep(
                    name = name, onNameChange = { name = it },
                    designation = designation, onDesignationChange = { designation = it },
                    organization = organization, onOrganizationChange = { organization = it },
                    onNext = { step = 1 }
                )
                1 -> PermissionStep(
                    icon = Icons.Filled.LocationOn,
                    title = "Location access",
                    explanation = "Seabuckthorn Field Survey uses your device's GPS to automatically record " +
                        "latitude, longitude and altitude for every survey. This works fully offline using the " +
                        "GPS satellite signal only — no internet or Google location service is needed. You can " +
                        "still save a survey if GPS is unavailable; the location fields will simply be marked unavailable.",
                    buttonLabel = "Allow location access",
                    onGrant = {
                        val alreadyGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) step = 2
                        else locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    onSkip = { step = 2 }
                )
                2 -> PermissionStep(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera access",
                    explanation = "The app lets you photograph shrubs, fruit and site conditions directly from " +
                        "within a survey. Photos are stored only on this device, tagged with the survey ID, date " +
                        "and GPS location, and are never uploaded automatically.",
                    buttonLabel = "Allow camera access",
                    onGrant = {
                        val alreadyGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) {
                            viewModel.saveProfileAndComplete(
                                SurveyorProfile(name = name.trim(), designation = designation.trim(), organization = organization.trim()),
                                onDone = onFinished
                            )
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onSkip = {
                        viewModel.saveProfileAndComplete(
                            SurveyorProfile(name = name.trim(), designation = designation.trim(), organization = organization.trim()),
                            onDone = onFinished
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileStep(
    name: String, onNameChange: (String) -> Unit,
    designation: String, onDesignationChange: (String) -> Unit,
    organization: String, onOrganizationChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Icon(Icons.Filled.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
    Spacer(Modifier.height(12.dp))
    Text("Create your surveyor profile", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "This appears on every survey you record so field data stays traceable to its observer.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    LabeledTextField(label = "Full name", value = name, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    LabeledTextField(label = "Designation", value = designation, onValueChange = onDesignationChange, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    LabeledTextField(label = "Organization", value = organization, onValueChange = onOrganizationChange, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Continue")
    }
}

@Composable
private fun PermissionStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    explanation: String,
    buttonLabel: String,
    onGrant: () -> Unit,
    onSkip: () -> Unit
) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(explanation, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text(buttonLabel) }
        androidx.compose.material3.TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Not now")
        }
    }
}
