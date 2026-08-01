package com.rhencloud.sleepyxposed.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhencloud.sleepyxposed.ConfigManager
import com.rhencloud.sleepyxposed.MediaMethod
import com.rhencloud.sleepyxposed.R
import com.rhencloud.sleepyxposed.RomDetector
import com.rhencloud.sleepyxposed.SleepyConfig

/** Lightweight Material3 settings form (no Miuix preference animations). */
@Composable
fun ConfigScreen() {
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.config_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.server_configuration))
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.enable_reporting),
                checked = enabled,
                onCheckedChange = { enabled = it }
            )
            Field(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = stringResource(R.string.server_url_label),
                keyboardType = KeyboardType.Uri
            )
            Field(
                value = secret,
                onValueChange = { secret = it },
                label = stringResource(R.string.server_secret_label),
                isPassword = true
            )
            Field(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = stringResource(R.string.device_id_label)
            )
            Field(
                value = showName,
                onValueChange = { showName = it },
                label = stringResource(R.string.display_name_label)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.media_section_title))
        SettingsCard {
            Text(
                text = stringResource(R.string.media_section_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SwitchRow(
                title = stringResource(R.string.media_enable_reporting),
                checked = mediaEnabled,
                onCheckedChange = { mediaEnabled = it }
            )
            Field(
                value = mediaDeviceId,
                onValueChange = { mediaDeviceId = it },
                label = stringResource(R.string.media_device_id_label)
            )
            Field(
                value = mediaShowName,
                onValueChange = { mediaShowName = it },
                label = stringResource(R.string.media_show_name_label)
            )
            Text(
                text = stringResource(R.string.media_method_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            MediaMethod.entries.forEach { method ->
                val title = methodTitles[method].orEmpty()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mediaMethod == method,
                                onClick = { mediaMethod = method },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mediaMethod == method,
                        onClick = { mediaMethod = method }
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            if (recommendationText != null) {
                Text(
                    text = recommendationText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            OutlinedButton(
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.media_grant_notification_access))
            }
        }

        Spacer(Modifier.height(20.dp))
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
                if (mediaEnabled && (mediaDeviceId.isBlank() || mediaShowName.isBlank())) {
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
                if (ConfigManager.saveConfig(context, config)) {
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
                    Toast.makeText(context, "Failed to save configuration", Toast.LENGTH_SHORT)
                        .show()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
        ) {
            Text(stringResource(R.string.save_configuration))
        }

        if (statusMessage != null) {
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Text(
                    text = statusMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.instructions_title))
        SettingsCard {
            Text(
                text = stringResource(R.string.instructions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
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

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
