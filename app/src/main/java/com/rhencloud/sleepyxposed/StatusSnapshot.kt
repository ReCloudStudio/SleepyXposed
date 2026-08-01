package com.rhencloud.sleepyxposed

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/** Immutable dashboard snapshot — no Compose types. */
data class StatusSnapshot(
    val moduleHookActive: Boolean,
    val reportingEnabled: Boolean,
    val mediaReportingEnabled: Boolean,
    val mediaMethod: String,
    val notificationListenerEnabled: Boolean,
    val configLooksComplete: Boolean,
    val configPath: String,
    val configPathExists: Boolean,
    val androidVersion: String,
    val apiLevel: Int,
    val deviceModel: String,
    val manufacturer: String,
    val brand: String,
    val romFamily: String,
    val recommendedMethod: String,
    val recommendationReason: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val packageName: String,
    val xposedFramework: String,
    val xposedApiVersion: String,
    val moduleChannel: String,
    val deviceLine: String,
    val systemLine: String
) {
    companion object {
        private val LSPOSED_PACKAGES =
            listOf(
                "org.lsposed.manager",
                "io.github.lsposed.manager",
                "org.lsposed.manager.tip"
            )

        fun collect(context: Context): StatusSnapshot {
            val config =
                runCatching { ConfigManager.loadConfig(context) }.getOrElse { SleepyConfig() }
            val recommendation = runCatching { RomDetector.recommend(context) }.getOrNull()
            val methodLabel =
                when (MediaMethod.fromString(config.mediaMethod)) {
                    MediaMethod.AUTO -> context.getString(R.string.media_method_auto)
                    MediaMethod.SYSTEM_HOOK ->
                        context.getString(R.string.media_method_system_hook)
                    MediaMethod.NOTIFICATION_LISTENER ->
                        context.getString(R.string.media_method_notification_listener)
                    MediaMethod.DUMPSYS_SHELL ->
                        context.getString(R.string.media_method_dumpsys_shell)
                }
            val recommendedLabel =
                when (recommendation?.method) {
                    MediaMethod.SYSTEM_HOOK ->
                        context.getString(R.string.media_method_system_hook)
                    MediaMethod.NOTIFICATION_LISTENER ->
                        context.getString(R.string.media_method_notification_listener)
                    else -> "—"
                }
            val path = runCatching { ConfigManager.getConfigFilePath(context) }.getOrElse { "" }
            val (verName, verCode) = appVersion(context)
            val manufacturer = Build.MANUFACTURER.orEmpty()
            val model = Build.MODEL.orEmpty()

            return StatusSnapshot(
                moduleHookActive = XposedProbe.isModuleActive(),
                reportingEnabled = config.enabled,
                mediaReportingEnabled = config.mediaEnabled,
                mediaMethod = methodLabel,
                notificationListenerEnabled = isNotificationListenerEnabled(context),
                configLooksComplete = config.hasRequiredFields(),
                configPath = path,
                configPathExists = path.isNotBlank() && File(path).exists(),
                androidVersion = "Android ${Build.VERSION.RELEASE}",
                apiLevel = Build.VERSION.SDK_INT,
                deviceModel = model,
                manufacturer = manufacturer,
                brand = Build.BRAND.orEmpty(),
                romFamily = recommendation?.rom?.displayName ?: "—",
                recommendedMethod = recommendedLabel,
                recommendationReason = recommendation?.reason.orEmpty(),
                appVersionName = verName,
                appVersionCode = verCode,
                packageName = context.packageName,
                xposedFramework = detectXposedFramework(context),
                xposedApiVersion = BuildConfig.XPOSED_API.toString(),
                moduleChannel = BuildConfig.MODULE_CHANNEL,
                deviceLine = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" "),
                systemLine = "${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})"
            )
        }

        private fun detectXposedFramework(context: Context): String {
            for (pkg in LSPOSED_PACKAGES) {
                if (isPackageInstalled(context, pkg)) {
                    val ver = packageVersionLabel(context, pkg)
                    return if (ver != null) "LSPosed ($ver)" else "LSPosed"
                }
            }
            // Bridge class presence (legacy / some environments)
            return try {
                Class.forName("de.robv.android.xposed.XposedBridge")
                "Xposed"
            } catch (_: Throwable) {
                context.getString(R.string.status_framework_unknown)
            }
        }

        private fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(packageName, 0)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun packageVersionLabel(context: Context, packageName: String): String? {
            return try {
                val pi =
                    if (Build.VERSION.SDK_INT >= 33) {
                        context.packageManager.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(packageName, 0)
                    }
                val code =
                    if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
                    else {
                        @Suppress("DEPRECATION")
                        pi.versionCode.toLong()
                    }
                code.toString()
            } catch (_: Exception) {
                null
            }
        }

        private fun appVersion(context: Context): Pair<String, Long> {
            return try {
                val pm = context.packageManager
                val pi =
                    if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageInfo(
                            context.packageName,
                            PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(context.packageName, 0)
                    }
                val name = pi.versionName ?: "1.0"
                val code =
                    if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
                    else {
                        @Suppress("DEPRECATION")
                        pi.versionCode.toLong()
                    }
                name to code
            } catch (_: Exception) {
                "1.0" to 1L
            }
        }

        private fun isNotificationListenerEnabled(context: Context): Boolean {
            return try {
                val flat =
                    Settings.Secure.getString(
                        context.contentResolver,
                        "enabled_notification_listeners"
                    ) ?: return false
                val pkg = context.packageName
                val cn = ComponentName(context, MediaListenerService::class.java)
                flat.split(':').any { entry ->
                    entry.equals(cn.flattenToString(), ignoreCase = true) ||
                        entry.startsWith("$pkg/")
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}

private fun SleepyConfig.hasRequiredFields(): Boolean {
    return serverUrl.isNotBlank() &&
        secret.isNotBlank() &&
        deviceId.isNotBlank() &&
        showName.isNotBlank()
}
