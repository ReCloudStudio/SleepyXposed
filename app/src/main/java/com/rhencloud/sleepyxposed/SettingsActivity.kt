package com.rhencloud.sleepyxposed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rhencloud.sleepyxposed.ui.ConfigScreen
import com.rhencloud.sleepyxposed.ui.SleepyMiuixTheme

/** Isolated settings activity so the home dashboard never composes the heavy form tree. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SleepyMiuixTheme {
                ConfigScreen(onBack = { finish() })
            }
        }
    }
}
