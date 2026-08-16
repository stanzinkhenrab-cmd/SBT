package com.kvk.leh.seabuckthorn.util

import android.content.Context
import android.content.Intent

/** Restarts the app process — required after a database restore so every component reopens the new file cleanly. */
object AppRestarter {
    fun restart(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            val restartIntent = Intent.makeRestartActivityTask(intent.component)
            context.startActivity(restartIntent)
        }
        Runtime.getRuntime().exit(0)
    }
}
