package io.github.recloudstudio.sleepyxposed.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.recloudstudio.sleepyxposed.R
import io.github.recloudstudio.sleepyxposed.StatusSnapshot

/**
 * HyperOShape-style overview: large title, solid status banner, simple key/value card.
 * Icons use material-icons-core only (filled) to avoid multi‑MB icons-extended DEX.
 */
@Composable
fun OverviewScreen(snapshot: StatusSnapshot?) {
    var showMore by remember { mutableStateOf(false) }
    // Single shared scroll state; no nested scrollables on this screen.
    val scroll = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, enabled = true)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 34.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(24.dp))

        if (snapshot == null) {
            Text(
                text = stringResource(R.string.status_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        StatusBanner(snapshot)
        Spacer(Modifier.height(16.dp))
        InfoCard(snapshot, showMore) { showMore = !showMore }
    }
}

@Composable
private fun StatusBanner(data: StatusSnapshot) {
    val active = data.moduleHookActive
    val bg = if (active) StatusActiveColor else StatusInactiveColor
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text =
                        if (active) stringResource(R.string.status_banner_enabled)
                        else stringResource(R.string.status_banner_disabled),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text =
                        stringResource(
                            R.string.status_banner_version,
                            data.appVersionName,
                            data.xposedApiVersion,
                            data.appVersionCode
                        ),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun InfoCard(data: StatusSnapshot, showMore: Boolean, onToggleMore: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            InfoItem(
                label = stringResource(R.string.info_xposed_framework),
                value = data.xposedFramework
            )
            InfoItem(
                label = stringResource(R.string.info_xposed_api),
                value = data.xposedApiVersion
            )
            InfoItem(
                label = stringResource(R.string.info_module_channel),
                value = data.moduleChannel
            )
            InfoItem(
                label = stringResource(R.string.info_package_name),
                value = data.packageName
            )
            InfoItem(
                label = stringResource(R.string.info_device),
                value = data.deviceLine.ifBlank { "—" }
            )
            InfoItem(
                label = stringResource(R.string.info_system_version),
                value = data.systemLine
            )

            // No AnimatedVisibility — animation during expand was extra cost; layout is the same.
            if (showMore) {
                InfoItem(
                    label = stringResource(R.string.status_last_heartbeat),
                    value = formatHeartbeatAge(data.lastHeartbeatAgoMs)
                )
                InfoItem(
                    label = stringResource(R.string.status_rom),
                    value = data.romFamily
                )
                InfoItem(
                    label = stringResource(R.string.status_reporting),
                    value =
                        if (data.reportingEnabled) stringResource(R.string.status_on)
                        else stringResource(R.string.status_off)
                )
                InfoItem(
                    label = stringResource(R.string.status_media_reporting),
                    value =
                        if (data.mediaReportingEnabled) stringResource(R.string.status_on)
                        else stringResource(R.string.status_off)
                )
                InfoItem(
                    label = stringResource(R.string.status_media_method),
                    value = data.mediaMethod
                )
                InfoItem(
                    label = stringResource(R.string.status_notification_listener),
                    value =
                        if (data.notificationListenerEnabled)
                            stringResource(R.string.status_granted)
                        else stringResource(R.string.status_not_granted)
                )
                InfoItem(
                    label = stringResource(R.string.status_config_complete),
                    value =
                        if (data.configLooksComplete) stringResource(R.string.status_ok)
                        else stringResource(R.string.status_incomplete)
                )
                if (data.configPath.isNotBlank()) {
                    InfoItem(
                        label = stringResource(R.string.status_config_path),
                        value =
                            data.configPath +
                                if (data.configPathExists) ""
                                else " (${stringResource(R.string.status_missing_file)})"
                    )
                }
                if (!data.moduleHookActive) {
                    Text(
                        text = stringResource(R.string.status_module_hook_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onToggleMore) {
                    Text(
                        text =
                            if (showMore) stringResource(R.string.less_info)
                            else stringResource(R.string.more_info),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 22.sp
        )
    }
}

/** "12s ago" / "3m ago" style summary; null/negative means we've never heard from the hook. */
private fun formatHeartbeatAge(agoMs: Long?): String {
    if (agoMs == null || agoMs < 0) return "—"
    val seconds = agoMs / 1000
    return when {
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ago"
    }
}
