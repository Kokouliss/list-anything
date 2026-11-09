package com.kokouliss.listanything

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kokouliss.listanything.core.data.isBackendConfigured
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Checked before any screen asks Hilt for Firebase, which would crash without a configuration.
        val isConfigured = isBackendConfigured(this)
        setContent {
            ListAnythingTheme {
                if (isConfigured) ListAnythingApp() else SetupNeededScreen()
            }
        }
    }
}
