package com.kvkleh.sbtsurvey.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands an exported file to the Android Sharesheet.
 *
 * No target application is hard-coded: whatever the surveyor has installed — WhatsApp,
 * Gmail, Telegram, Drive, Bluetooth, a file manager — appears in the standard chooser.
 */
object ShareLauncher {

    fun share(context: Context, result: ExportResult) {
        share(
            context = context,
            file = result.file,
            mimeType = result.format.mimeType,
            body = buildString {
                appendLine("Seabuckthorn Field Survey – Ladakh")
                appendLine("Krishi Vigyan Kendra – Leh | MIDH-SBM")
                appendLine()
                appendLine("File: ${result.file.name}")
                appendLine("Survey records: ${result.recordCount}")
                if (result.photoCount > 0) appendLine("Photographs: ${result.photoCount}")
            }
        )
    }

    fun share(
        context: Context,
        file: File,
        mimeType: String,
        body: String,
        subject: String = "Seabuckthorn Field Survey – Ladakh"
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share survey data")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
