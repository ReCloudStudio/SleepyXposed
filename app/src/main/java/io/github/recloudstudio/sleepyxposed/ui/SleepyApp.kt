package io.github.recloudstudio.sleepyxposed.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.recloudstudio.sleepyxposed.R
import io.github.recloudstudio.sleepyxposed.StatusSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab {
    Overview,
    Config
}

@Composable
fun SleepyApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Overview) }
    var snapshot by remember { mutableStateOf<StatusSnapshot?>(null) }
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    fun refresh() {
        scope.launch {
            // StatusSnapshot.collect() is blocking I/O (SharedPreferences/file reads,
            // PackageManager + Settings.Secure binder calls), not CPU-bound work, so it
            // belongs on Dispatchers.IO — matching how the rest of the app (ConfigScreen)
            // already offloads the same kind of work. Dispatchers.Default is sized for
            // CPU-bound work and shouldn't be used for blocking calls.
            snapshot = withContext(Dispatchers.IO) { StatusSnapshot.collect(context) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                // Only refresh when returning to the activity (e.g. after system settings).
                if (event == Lifecycle.Event.ON_RESUME) refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                // Match HyperOShape: config left, overview (home) right
                NavigationBarItem(
                    selected = tab == Tab.Config,
                    onClick = { tab = Tab.Config },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_config)) },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = tab == Tab.Overview,
                    onClick = { tab = Tab.Overview },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_overview)) },
                    colors = navColors
                )
            }
        }
    ) { padding ->
        // Only the active tab is composed.
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Overview -> OverviewScreen(snapshot = snapshot)
                Tab.Config -> ConfigScreen()
            }
        }
    }
}
