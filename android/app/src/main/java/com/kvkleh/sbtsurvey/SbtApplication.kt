package com.kvkleh.sbtsurvey

import android.app.Application
import android.util.Log

class SbtApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        installCrashLogger()
    }

    /**
     * Survey data is written to the database as it is typed, so a crash costs at
     * most the last keystroke. The handler only records what happened before the
     * platform's own handler takes over.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e("SbtSurvey", "Uncaught exception on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
