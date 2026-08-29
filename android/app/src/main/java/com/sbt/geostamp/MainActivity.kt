package com.sbt.geostamp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sbt.geostamp.io.ImageStore
import com.sbt.geostamp.ui.CameraScreen
import com.sbt.geostamp.ui.EditorScreen
import com.sbt.geostamp.ui.EditorViewModel
import com.sbt.geostamp.ui.HomeScreen
import com.sbt.geostamp.ui.theme.GeoStampTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedImage = incomingImage(intent)
        setContent {
            GeoStampTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GeoStampRoot(sharedImage)
                }
            }
        }
    }

    private fun incomingImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

private enum class Screen { HOME, CAMERA, EDITOR }

@Composable
private fun GeoStampRoot(sharedImage: Uri?) {
    val viewModel: EditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var screen by remember { mutableStateOf(Screen.HOME) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) viewModel.refreshLocation()
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) screen = Screen.CAMERA
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.openPhoto(uri)
            screen = Screen.EDITOR
        }
    }

    fun askForLocation() {
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(sharedImage) {
        if (sharedImage != null) {
            viewModel.openPhoto(sharedImage)
            screen = Screen.EDITOR
            askForLocation()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            context.startActivity(
                Intent.createChooser(ImageStore.shareIntent(uri), "Share stamped photo")
            )
            viewModel.consumeShareUri()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                onTakePhoto = { cameraPermission.launch(Manifest.permission.CAMERA) },
                onPickPhoto = {
                    askForLocation()
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.padding(padding)
            )

            Screen.CAMERA -> CameraScreen(
                onCaptured = { uri ->
                    viewModel.openPhoto(uri)
                    askForLocation()
                    screen = Screen.EDITOR
                },
                onClose = { screen = Screen.HOME },
                onError = { screen = Screen.HOME }
            )

            Screen.EDITOR -> EditorScreen(
                state = state,
                onBack = {
                    viewModel.discardPhoto()
                    screen = Screen.HOME
                },
                onSave = viewModel::save,
                onShare = viewModel::share,
                onRefreshLocation = {
                    if (state.locationDenied) askForLocation() else viewModel.refreshLocation()
                },
                onApplyCoordinates = viewModel::applyManualLocation,
                onContentChange = viewModel::updateContent,
                onOptionsChange = viewModel::updateOptions,
                modifier = Modifier.padding(padding)
            )
        }
    }
}
