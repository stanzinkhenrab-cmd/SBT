package com.kvkleh.sbtsurvey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kvkleh.sbtsurvey.ui.nav.SbtNavHost
import com.kvkleh.sbtsurvey.ui.theme.SbtTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SbtTheme {
                SbtNavHost()
            }
        }
    }
}
