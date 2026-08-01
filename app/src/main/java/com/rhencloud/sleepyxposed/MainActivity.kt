package com.rhencloud.sleepyxposed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.rhencloud.sleepyxposed.ui.SleepyApp
import com.rhencloud.sleepyxposed.ui.SleepyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            true
        setContent {
            SleepyTheme {
                SleepyApp()
            }
        }
    }
}
