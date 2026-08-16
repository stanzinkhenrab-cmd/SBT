package com.kvkleh.sbtsurvey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kvkleh.sbtsurvey.ui.nav.SbtNavHost
import com.kvkleh.sbtsurvey.ui.theme.SbtSurveyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The graph is normally initialised by the Application; do it here too so
        // the activity is safe to start in isolation (tests, process restarts).
        Graph.init(applicationContext)
        setContent {
            SbtSurveyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SbtNavHost()
                }
            }
        }
    }
}
