package com.kvkleh.sbtsurvey.ui.survey

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import java.io.File

/**
 * Photograph capture and preview.
 *
 * The camera writes straight into the app's permanent photo directory through a
 * FileProvider URI, so the image is linked to the Survey ID the moment it is taken —
 * there is no temporary URI that could expire before the record is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoSection(
    survey: SurveyEntity,
    newPhotoTarget: () -> File?,
    uriFor: (File) -> Uri,
    onCaptured: (File) -> Unit,
    onPicked: (Uri) -> Unit,
    onDeleted: () -> Unit,
    onDiscardTarget: (File?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null && file.exists() && file.length() > 0) {
            errorText = null
            onCaptured(file)
        } else {
            onDiscardTarget(file)
            if (!success) errorText = "Photograph was not taken."
        }
    }

    fun launchCamera() {
        val file = newPhotoTarget()
        if (file == null) {
            errorText = "Camera is unavailable. Please check camera permission."
            return
        }
        pendingFile = file
        val launched = runCatching { cameraLauncher.launch(uriFor(file)) }
        if (launched.isFailure) {
            onDiscardTarget(file)
            pendingFile = null
            errorText = "Camera is unavailable. Please check camera permission."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            errorText = "Camera is unavailable. Please check camera permission."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            errorText = null
            onPicked(uri)
        }
    }

    SectionCard(
        title = "Photo",
        number = 3,
        subtitle = "Stored permanently with this Survey ID",
        modifier = modifier
    ) {
        val photo = survey.photoPath?.let { File(it) }

        if (photo != null && photo.exists() && photo.length() > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photo)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Survey photograph preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column {
                Text(
                    text = survey.photoFileName ?: photo.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Captured ${Fmt.dateTime(survey.photoCapturedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No photograph yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        errorText?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        launchCamera()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.heightIn(min = 54.dp)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (photo != null) "Retake Photo" else "Take Photo")
            }

            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.heightIn(min = 54.dp)
            ) {
                Icon(Icons.Filled.Collections, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose from Device")
            }

            if (photo != null) {
                TextButton(
                    onClick = onDeleted,
                    modifier = Modifier.heightIn(min = 54.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Photo", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
