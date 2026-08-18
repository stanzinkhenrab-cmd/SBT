package com.kvkleh.sbtsurvey.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Copies a generated file to wherever the surveyor chooses, through the system document
 * picker.
 *
 * Exports are generated inside the app's own storage, which is deliberately unreachable
 * from a file manager. This is what turns "Save" into something the surveyor can actually
 * find afterwards — Downloads, an SD card, or a cloud folder.
 */
class FileSaver internal constructor(
    private val start: (File) -> Unit
) {
    fun save(file: File) = start(file)
}

@Composable
fun rememberFileSaver(onFinished: (savedName: String?, error: String?) -> Unit): FileSaver {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_ANY)
    ) { destination ->
        val source = pending
        pending = null
        when {
            destination == null || source == null -> onFinished(null, null) // cancelled
            else -> {
                val outcome = runCatching {
                    context.contentResolver.openOutputStream(destination)
                        ?.use { output -> source.inputStream().use { it.copyTo(output) } }
                        ?: error("The chosen location could not be opened for writing.")
                }
                if (outcome.isSuccess) {
                    onFinished(source.name, null)
                } else {
                    onFinished(null, "The file could not be saved to that location.")
                }
            }
        }
    }

    return remember(launcher) {
        FileSaver { file ->
            pending = file
            runCatching { launcher.launch(file.name) }
                .onFailure {
                    pending = null
                    onFinished(null, "No app on this device can save files.")
                }
        }
    }
}

/**
 * The picker keeps the extension from the suggested file name, so a permissive type here
 * lets one launcher handle CSV, XLSX, ZIP, PDF, TIFF and images alike.
 */
private const val MIME_ANY = "*/*"
