package com.rhencloud.sleepyxposed.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import com.rhencloud.sleepyxposed.R
import com.rhencloud.sleepyxposed.StatusSnapshot
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

    fun refresh() {
        scope.launch {
            snapshot = withContext(Dispatchers.Default) { StatusSnapshot.collect(context) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
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
                            imageVector =
                                if (tab == Tab.Config) Icons.Filled.Settings
                                else Icons.Outlined.Settings,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_config)) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
                NavigationBarItem(
                    selected = tab == Tab.Overview,
                    onClick = {
                        tab = Tab.Overview
                        refresh()
                    },
                    icon = {
                        Icon(
                            imageVector =
                                if (tab == Tab.Overview) Icons.Filled.Home
                                else Icons.Outlined.Home,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_overview)) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
            }
        }
    ) { padding ->
        // Only the active tab is composed — avoids keeping heavy form widgets alive on overview.
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Overview -> OverviewScreen(snapshot = snapshot)
                Tab.Config -> ConfigScreen()
            }
        }
    }
}
