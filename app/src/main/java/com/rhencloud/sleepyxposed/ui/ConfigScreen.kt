package com.rhencloud.sleepyxposed.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rhencloud.sleepyxposed.ConfigManager
import com.rhencloud.sleepyxposed.MediaMethod
import com.rhencloud.sleepyxposed.R
import com.rhencloud.sleepyxposed.RomDetector
import com.rhencloud.sleepyxposed.SleepyConfig
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Settings form isolated from the home dashboard.
 *
 * Uses a single [verticalScroll] [Column] instead of [LazyColumn] with huge items — fewer
 * measure/pass costs for a short form, and TextFields are not wrapped in nested lazy items.
 */
@Composable
fun ConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val initial =
        remember {
            runCatching { ConfigManager.loadConfig(context) }.getOrElse { SleepyConfig() }
        }

    var serverUrl by remember { mutableStateOf(initial.serverUrl) }
    var secret by remember { mutableStateOf(initial.secret) }
    var deviceId by remember { mutableStateOf(initial.deviceId) }
    var showName by remember { mutableStateOf(initial.showName) }
    var enabled by remember { mutableStateOf(initial.enabled) }

    var mediaEnabled by remember { mutableStateOf(initial.mediaEnabled) }
    var mediaDeviceId by remember { mutableStateOf(initial.mediaDeviceId) }
    var mediaShowName by remember { mutableStateOf(initial.mediaShowName) }
    var mediaMethod by remember {
        mutableStateOf(MediaMethod.fromString(initial.mediaMethod))
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Cache recommendation once; avoid re-running property probes on every keystroke.
    val recommendationText =
        remember {
            runCatching {
                    val recommendation = RomDetector.recommend(context)
                    val methodLabel =
                        when (recommendation.method) {
                            MediaMethod.SYSTEM_HOOK ->
                                context.getString(R.string.media_method_system_hook)
                            MediaMethod.NOTIFICATION_LISTENER ->
                                context.getString(R.string.media_method_notification_listener)
                            else -> context.getString(R.string.media_method_system_hook)
                        }
                    context.getString(
                        R.string.media_recommendation_format,
                        recommendation.androidVersion,
                        recommendation.rom.displayName,
                        methodLabel,
                        recommendation.reason
                    )
                }
                .getOrNull()
        }

    val methodTitles =
        remember {
            MediaMethod.entries.associateWith { method ->
                when (method) {
                    MediaMethod.AUTO -> context.getString(R.string.media_method_auto)
                    MediaMethod.SYSTEM_HOOK ->
                        context.getString(R.string.media_method_system_hook)
                    MediaMethod.NOTIFICATION_LISTENER ->
                        context.getString(R.string.media_method_notification_listener)
                    MediaMethod.DUMPSYS_SHELL ->
                        context.getString(R.string.media_method_dumpsys_shell)
                }
            }
        }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    TextButton(
                        text = stringResource(R.string.action_back),
                        onClick = onBack
                    )
                }
            )
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
            Text(
                text = stringResource(R.string.config_description),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            SmallTitle(text = stringResource(R.string.server_configuration))
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    ConfigField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = stringResource(R.string.server_url_label),
                        keyboardType = KeyboardType.Uri
                    )
                    ConfigField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = stringResource(R.string.server_secret_label),
                        isPassword = true
                    )
                    ConfigField(
                        value = deviceId,
                        onValueChange = { deviceId = it },
                        label = stringResource(R.string.device_id_label)
                    )
                    ConfigField(
                        value = showName,
                        onValueChange = { showName = it },
                        label = stringResource(R.string.display_name_label)
                    )
                }
                SuperSwitch(
                    title = stringResource(R.string.enable_reporting),
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }

            SmallTitle(text = stringResource(R.string.media_section_title))
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.media_section_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                SuperSwitch(
                    title = stringResource(R.string.media_enable_reporting),
                    checked = mediaEnabled,
                    onCheckedChange = { mediaEnabled = it }
                )
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ConfigField(
                        value = mediaDeviceId,
                        onValueChange = { mediaDeviceId = it },
                        label = stringResource(R.string.media_device_id_label)
                    )
                    ConfigField(
                        value = mediaShowName,
                        onValueChange = { mediaShowName = it },
                        label = stringResource(R.string.media_show_name_label)
                    )
                }

                SmallTitle(text = stringResource(R.string.media_method_label))
                MediaMethod.entries.forEach { method ->
                    val title = methodTitles[method].orEmpty()
                    SuperRadioButton(
                        title = title,
                        selected = mediaMethod == method,
                        onClick = { mediaMethod = method }
                    )
                }

                if (recommendationText != null) {
                    Text(
                        text = recommendationText,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        } catch (e: Exception) {
                            Toast.makeText(
                                    context,
                                    "Failed to open settings: ${e.message}",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text(text = stringResource(R.string.media_grant_notification_access))
                }
            }

            Button(
                onClick = {
                    if (serverUrl.isBlank() ||
                        secret.isBlank() ||
                        deviceId.isBlank() ||
                        showName.isBlank()
                    ) {
                        Toast.makeText(
                                context,
                                context.getString(R.string.fill_all_fields),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                        return@Button
                    }
                    if (mediaEnabled &&
                        (mediaDeviceId.isBlank() || mediaShowName.isBlank())
                    ) {
                        Toast.makeText(
                                context,
                                context.getString(R.string.media_fill_required_fields),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                        return@Button
                    }

                    val config =
                        SleepyConfig(
                            serverUrl = serverUrl.trim(),
                            secret = secret,
                            deviceId = deviceId.trim(),
                            showName = showName.trim(),
                            enabled = enabled,
                            mediaEnabled = mediaEnabled,
                            mediaDeviceId = mediaDeviceId.trim(),
                            mediaShowName = mediaShowName.trim(),
                            mediaMethod = mediaMethod.name
                        )

                    val success = ConfigManager.saveConfig(context, config)
                    if (success) {
                        statusMessage =
                            context.getString(R.string.config_saved) +
                                "\n\n" +
                                ConfigManager.getConfigFilePath(context)
                        Toast.makeText(
                                context,
                                context.getString(R.string.config_saved_toast),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    } else {
                        statusMessage = null
                        Toast.makeText(
                                context,
                                "Failed to save configuration",
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = stringResource(R.string.save_configuration))
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = statusMessage!!,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SmallTitle(text = stringResource(R.string.instructions_title))
            Card {
                Text(
                    text = stringResource(R.string.instructions),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
    )
}
