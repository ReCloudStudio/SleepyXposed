package com.rhencloud.sleepyxposed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun SleepyMiuixTheme(content: @Composable () -> Unit) {
    // Single controller instance; avoid reallocating color pipelines on each recomposition.
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller, content = content)
}
