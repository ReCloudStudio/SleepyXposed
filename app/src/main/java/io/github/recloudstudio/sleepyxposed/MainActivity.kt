package io.github.recloudstudio.sleepyxposed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.recloudstudio.sleepyxposed.ui.SleepyApp
import io.github.recloudstudio.sleepyxposed.ui.SleepyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepyTheme {
                SleepyApp()
            }
        }
    }
}
