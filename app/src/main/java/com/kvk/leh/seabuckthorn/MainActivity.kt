package com.kvk.leh.seabuckthorn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kvk.leh.seabuckthorn.ui.navigation.Destinations
import com.kvk.leh.seabuckthorn.ui.navigation.SeabuckthornNavGraph
import com.kvk.leh.seabuckthorn.ui.theme.SeabuckthornTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var startDestination by mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination == null }

        val app = application as SeabuckthornApp

        setContent {
            val onboardingCompleted by app.appPreferences.onboardingCompleted.collectAsState(initial = null)
            LaunchedEffect(onboardingCompleted) {
                val completed = onboardingCompleted
                if (completed != null && startDestination == null) {
                    startDestination = if (completed) Destinations.DASHBOARD else Destinations.ONBOARDING
                }
            }

            SeabuckthornTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val destination = startDestination
                    if (destination != null) {
                        SeabuckthornNavGraph(startDestination = destination)
                    }
                }
            }
        }
    }
}
