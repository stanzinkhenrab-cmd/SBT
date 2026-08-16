package com.kvk.leh.seabuckthorn.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.kvk.leh.seabuckthorn.camera.CameraController
import com.kvk.leh.seabuckthorn.camera.WatermarkUtil
import com.kvk.leh.seabuckthorn.data.photo.PhotoStorage
import com.kvk.leh.seabuckthorn.domain.model.PhotoCategory
import com.kvk.leh.seabuckthorn.util.DateUtils
import kotlinx.coroutines.launch

/**
 * Full-screen camera capture overlay, shown from within the Photos wizard step (or the survey
 * detail screen). Entirely local: CameraX preview + capture, with an optional GPS/ID watermark
 * burned in only when the user turns it on for that photo.
 */
@Composable
fun PhotoCaptureOverlay(
    surveyCode: String,
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    nextPhotoNumber: Int,
    onCaptured: (filePath: String, category: PhotoCategory, watermarked: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    var selectedCategory by remember { mutableStateOf(PhotoCategory.WHOLE_SHRUB) }
    var watermarkEnabled by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            val cameraController = remember { CameraController(context) }
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(Unit) {
                cameraController.bindToLifecycle(lifecycleOwner, previewView)
            }
            DisposableEffect(Unit) {
                onDispose { cameraController.unbind() }
            }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close camera", tint = Color.White)
                    }
                    Text("Photo category", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhotoCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Switch(checked = watermarkEnabled, onCheckedChange = { watermarkEnabled = it })
                    Spacer(Modifier.size(8.dp))
                    Text("Watermark with Survey ID | Date | GPS", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (isCapturing) return@IconButton
                        isCapturing = true
                        val outputFile = PhotoStorage.newPhotoFile(context, surveyCode, selectedCategory.name, nextPhotoNumber)
                        scope.launch {
                            runCatching {
                                val savedFile = cameraController.takePhoto(outputFile)
                                if (watermarkEnabled) {
                                    val lines = WatermarkUtil.watermarkLines(
                                        surveyCode, DateUtils.epochDayToDisplay(DateUtils.todayEpochDay()), latitude, longitude, altitude
                                    )
                                    WatermarkUtil.applyWatermark(savedFile, lines)
                                }
                                onCaptured(savedFile.absolutePath, selectedCategory, watermarkEnabled)
                            }
                            isCapturing = false
                        }
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Capture photo", tint = Color.Black, modifier = Modifier.size(34.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Camera permission is required to photograph shrubs and fruit.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant camera permission")
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
