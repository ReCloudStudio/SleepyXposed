package com.rhencloud.sleepyxposed.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rhencloud.sleepyxposed.R
import com.rhencloud.sleepyxposed.SettingsActivity
import com.rhencloud.sleepyxposed.StatusSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Lightweight dashboard. Heavy form widgets live only in [SettingsActivity] so they are not
 * composed or animated while the user stays on home.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<StatusSnapshot?>(null) }

    fun refresh() {
        scope.launch {
            snapshot = withContext(Dispatchers.Default) { StatusSnapshot.collect(context) }
        }
    }

    // Refresh on first composition and whenever the activity resumes (e.g. after Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = stringResource(R.string.app_name))
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val data = snapshot
            if (data == null) {
                Text(
                    text = stringResource(R.string.status_loading),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                ModuleStatusCard(data)
                Spacer(Modifier.height(8.dp))
                OperationalStatusCard(data)
                Spacer(Modifier.height(8.dp))
                SystemInfoCard(data)
            }

            Spacer(Modifier.height(12.dp))
            SmallTitle(text = stringResource(R.string.home_section_actions))
            Card {
                SuperArrow(
                    title = stringResource(R.string.open_settings),
                    summary = stringResource(R.string.open_settings_summary),
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModuleStatusCard(data: StatusSnapshot) {
    SmallTitle(text = stringResource(R.string.home_section_module))
    Card {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            StatusRow(
                title = stringResource(R.string.status_module_hook),
                value =
                    if (data.moduleHookActive) stringResource(R.string.status_active)
                    else stringResource(R.string.status_inactive),
                active = data.moduleHookActive
            )
            Text(
                text = stringResource(R.string.status_module_hook_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )
            InfoLine(
                stringResource(R.string.status_app_version),
                "v${data.appVersionName} (${data.appVersionCode})"
            )
        }
    }
}

@Composable
private fun OperationalStatusCard(data: StatusSnapshot) {
    SmallTitle(text = stringResource(R.string.home_section_runtime))
    Card {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            StatusRow(
                title = stringResource(R.string.status_reporting),
                value =
                    if (data.reportingEnabled) stringResource(R.string.status_on)
                    else stringResource(R.string.status_off),
                active = data.reportingEnabled
            )
            Spacer(Modifier.height(8.dp))
            StatusRow(
                title = stringResource(R.string.status_media_reporting),
                value =
                    if (data.mediaReportingEnabled) stringResource(R.string.status_on)
                    else stringResource(R.string.status_off),
                active = data.mediaReportingEnabled
            )
            Spacer(Modifier.height(8.dp))
            InfoLine(stringResource(R.string.status_media_method), data.mediaMethod)
            Spacer(Modifier.height(8.dp))
            StatusRow(
                title = stringResource(R.string.status_notification_listener),
                value =
                    if (data.notificationListenerEnabled) stringResource(R.string.status_granted)
                    else stringResource(R.string.status_not_granted),
                active = data.notificationListenerEnabled
            )
            Spacer(Modifier.height(8.dp))
            StatusRow(
                title = stringResource(R.string.status_config_complete),
                value =
                    if (data.configLooksComplete) stringResource(R.string.status_ok)
                    else stringResource(R.string.status_incomplete),
                active = data.configLooksComplete
            )
            if (data.configPath.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoLine(
                    stringResource(R.string.status_config_path),
                    data.configPath +
                        if (data.configPathExists) ""
                        else " (${stringResource(R.string.status_missing_file)})"
                )
            }
        }
    }
}

@Composable
private fun SystemInfoCard(data: StatusSnapshot) {
    SmallTitle(text = stringResource(R.string.home_section_system))
    Card {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            InfoLine(stringResource(R.string.status_android), "${data.androidVersion} · API ${data.apiLevel}")
            Spacer(Modifier.height(6.dp))
            InfoLine(stringResource(R.string.status_device), "${data.manufacturer} ${data.deviceModel}")
            Spacer(Modifier.height(6.dp))
            InfoLine(stringResource(R.string.status_brand), data.brand)
            Spacer(Modifier.height(6.dp))
            InfoLine(stringResource(R.string.status_rom), data.romFamily)
            Spacer(Modifier.height(6.dp))
            InfoLine(stringResource(R.string.status_recommended_method), data.recommendedMethod)
            if (data.recommendationReason.isNotBlank()) {
                Text(
                    text = data.recommendationReason,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, value: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dot =
            if (active) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantSummary
        Spacer(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}
