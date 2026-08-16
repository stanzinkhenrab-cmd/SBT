package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kvk.leh.seabuckthorn.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads and displays a downsampled thumbnail for a local photo file path. */
@Composable
fun PhotoThumbnail(filePath: String, modifier: Modifier = Modifier) {
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(filePath) {
        val decoded = withContext(Dispatchers.IO) { BitmapUtils.decodeSampled(filePath, 300, 300) }
        bitmap = decoded?.asImageBitmap()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(bitmap = current, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.ImageIcon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
