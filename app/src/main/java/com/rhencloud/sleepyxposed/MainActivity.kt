package com.rhencloud.sleepyxposed

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = controller) {
                SleepyConfigScreen()
            }
        }
    }
}

@Composable
private fun SleepyConfigScreen() {
    val context = LocalContext.current
    val initial =
        remember {
            runCatching { ConfigManager.loadConfig(context) }
                .getOrElse { SleepyConfig() }
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

    val recommendation =
        remember {
            runCatching { RomDetector.recommend(context) }.getOrNull()
        }
    val recommendationText =
        remember(recommendation) {
            if (recommendation == null) {
                null
            } else {
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
        }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = stringResource(R.string.app_name))
        }
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 32.dp
                )
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.config_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item(key = "server") {
                SmallTitle(text = stringResource(R.string.server_configuration))
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        MiuixField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = stringResource(R.string.server_url_label),
                            keyboardType = KeyboardType.Uri
                        )
                        MiuixField(
                            value = secret,
                            onValueChange = { secret = it },
                            label = stringResource(R.string.server_secret_label),
                            isPassword = true
                        )
                        MiuixField(
                            value = deviceId,
                            onValueChange = { deviceId = it },
                            label = stringResource(R.string.device_id_label)
                        )
                        MiuixField(
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
            }

            item(key = "media") {
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
                        MiuixField(
                            value = mediaDeviceId,
                            onValueChange = { mediaDeviceId = it },
                            label = stringResource(R.string.media_device_id_label)
                        )
                        MiuixField(
                            value = mediaShowName,
                            onValueChange = { mediaShowName = it },
                            label = stringResource(R.string.media_show_name_label)
                        )
                    }

                    SmallTitle(
                        text = stringResource(R.string.media_method_label),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    MediaMethod.entries.forEach { method ->
                        SuperRadioButton(
                            title = methodTitle(method),
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
            }

            item(key = "save") {
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
            }

            item(key = "help") {
                SmallTitle(text = stringResource(R.string.instructions_title))
                Card {
                    Text(
                        text = stringResource(R.string.instructions),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixField(
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

@Composable
private fun methodTitle(method: MediaMethod): String =
    when (method) {
        MediaMethod.AUTO -> stringResource(R.string.media_method_auto)
        MediaMethod.SYSTEM_HOOK -> stringResource(R.string.media_method_system_hook)
        MediaMethod.NOTIFICATION_LISTENER ->
            stringResource(R.string.media_method_notification_listener)
        MediaMethod.DUMPSYS_SHELL -> stringResource(R.string.media_method_dumpsys_shell)
    }
