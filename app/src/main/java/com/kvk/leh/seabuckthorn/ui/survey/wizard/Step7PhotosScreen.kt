package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import com.kvk.leh.seabuckthorn.domain.model.PhotoCategory
import com.kvk.leh.seabuckthorn.ui.components.ConfirmDialog
import com.kvk.leh.seabuckthorn.ui.components.PhotoThumbnail
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@Composable
fun Step7PhotosScreen(
    photos: List<PhotoEntity>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var photoPendingDelete by remember { mutableStateOf<PhotoEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Photographs (${photos.size})", subtitle = "Whole shrub, stem, leaves, fruit at each stage, clusters, close-ups and site photos.") {
            Button(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Text("  Add Photo")
            }
        }
        if (photos.isEmpty()) {
            Text("No photographs yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos, key = { it.id }) { photo ->
                    Card(modifier = Modifier.aspectRatio(1f)) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PhotoThumbnail(filePath = photo.filePath, modifier = Modifier.fillMaxSize())
                            IconButton(
                                onClick = { photoPendingDelete = photo },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White)
                            }
                            val categoryLabel = runCatching { PhotoCategory.valueOf(photo.category).label }.getOrDefault(photo.category)
                            Text(
                                text = categoryLabel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    photoPendingDelete?.let { photo ->
        ConfirmDialog(
            title = "Remove photo?",
            message = "This photo will be permanently deleted from the device.",
            onConfirm = {
                onRemovePhoto(photo)
                photoPendingDelete = null
            },
            onDismiss = { photoPendingDelete = null }
        )
    }
}
