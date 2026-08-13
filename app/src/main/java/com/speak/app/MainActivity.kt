package com.speak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.speak.app.data.prefs.SettingsStore
import com.speak.app.ui.nav.SpeakNavHost
import com.speak.app.ui.onboarding.PermissionGate
import com.speak.app.ui.theme.SpeakTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SpeakApplication).container

        setContent {
            val settings by container.settings.settings
                .collectAsState(initial = SettingsStore.Settings())

            SpeakTheme(darkThemeSetting = settings.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var micGranted by remember { mutableStateOf(false) }

                    if (micGranted) {
                        SpeakNavHost(container = container)
                    } else {
                        PermissionGate(onGranted = { micGranted = true })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // Release the native models: together they hold well over a gigabyte,
            // and leaving them mapped after the app closes is inconsiderate on a
            // 6 GB phone.
            val container = (application as SpeakApplication).container
            container.ttsEngine.shutdown()
            container.releaseModels()
        }
    }
}
