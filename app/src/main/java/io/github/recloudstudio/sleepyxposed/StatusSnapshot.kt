package io.github.recloudstudio.sleepyxposed

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/** Immutable dashboard snapshot — no Compose types. */
data class StatusSnapshot(
    val moduleHookActive: Boolean,
    val lastHeartbeatAgoMs: Long?,
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
            val path = runCatching { ConfigManager.getConfigFilePath(context) }.getOrElse { "" }
            val (verName, verCode) = appVersion(context)
            val manufacturer = Build.MANUFACTURER.orEmpty()
            val model = Build.MODEL.orEmpty()

            return StatusSnapshot(
                moduleHookActive = HookHeartbeat.isRecentlyActive(context) || XposedProbe.isModuleActive(),
                lastHeartbeatAgoMs = HookHeartbeat.lastSeenMillisAgo(context),
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

        /**
         * Read the framework name/version straight from [HookHeartbeat] — pushed by
         * [ForegroundAppMonitor] directly from the libxposed `XposedInterface` this module is
         * attached to (`getFrameworkName()` / `getFrameworkVersion()`). That's the authoritative
         * source; it does not depend on whether a standalone LSPosed manager app happens to be
         * installed (most people never install one), nor is it affected by package-visibility
         * filtering (API 30+), which made an earlier "check if this package is installed"
         * heuristic unreliable.
         */
        private fun detectXposedFramework(context: Context): String {
            val info = HookHeartbeat.frameworkInfo(context) ?: return context.getString(R.string.status_framework_unknown)
            return if (info.version.isNotBlank()) "${info.name} (${info.version})" else info.name
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
