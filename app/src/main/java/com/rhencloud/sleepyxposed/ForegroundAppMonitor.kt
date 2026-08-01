package com.rhencloud.sleepyxposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.lang.reflect.Method
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

class ForegroundAppMonitor(private val log: (String) -> Unit) {

    companion object {
        private const val TAG = "SleepyXposed"
        private const val REPORT_DELAY_MS = 1000L
        private const val MATCH_ANY_USER_FLAG = 0x00002000
        private const val LOCK_REPORT_COOLDOWN_MS = 1_000L
        private const val ACTION_LOG_PREFIX = "LockReceiver action="

        private var lastForegroundPackage: String? = null
        private var currentForegroundPackage: String? = null
        private var currentForegroundActivity: String? = null
        private var lockReceiverRegistered: Boolean = false
        private var lastLockReportAt: Long = 0L

        var cachedConfig: Config? = null

        @JvmStatic
        fun getCurrentForegroundPackage(): String? = currentForegroundPackage

        @JvmStatic
        fun getCurrentForegroundActivity(): String? = currentForegroundActivity

        @JvmStatic
        fun getCurrentForegroundComponentName(): String? {
            return currentForegroundPackage?.let { pkg ->
                currentForegroundActivity?.let { activity -> "$pkg/$activity" } ?: pkg
            }
        }

        @JvmStatic
        fun getAppDisplayName(context: Context, packageName: String): String {
            return try {
                val packageManager = context.packageManager
                val applicationInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val flags =
                            PackageManager.ApplicationInfoFlags.of(MATCH_ANY_USER_FLAG.toLong())
                        packageManager.getApplicationInfo(packageName, flags)
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(packageName, 0)
                    }
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (_: Exception) {
                packageName
            }
        }
    }

    data class Config(
        val url: String,
        val secret: String,
        val id: String,
        val showName: String,
        val enabled: Boolean = true
    )

    private var handler: Handler? = null
    private var reportRunnable: Runnable? = null
    private var systemContext: Context? = null

    fun initializeForSystemServer(classLoader: ClassLoader) {
        log("$TAG: Detected system server, starting hook initialization...")
        try {
            hookActivityTaskManagerService(classLoader)
        } catch (e: Throwable) {
            log("$TAG: Failed to hook: ${e.message}")
        }
    }

    private fun hookActivityTaskManagerService(classLoader: ClassLoader) {
        log("$TAG: Starting hookActivityTaskManagerService...")

        val activityRecordClass = Class.forName("com.android.server.wm.ActivityRecord", false, classLoader)
        log("$TAG: Found ActivityRecord class")

        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread", false, classLoader)
            val currentActivityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val getSystemContext = activityThreadClass.getMethod("getSystemContext")
            systemContext = getSystemContext.invoke(currentActivityThread) as? Context

            log("$TAG: System context obtained: $systemContext")
            if (systemContext != null) {
                val looper = systemContext?.mainLooper ?: Looper.myLooper() ?: Looper.getMainLooper()
                handler = Handler(looper)
                loadConfiguration()
                registerLockScreenReceiver()
                // Prove the hook is alive as soon as bootstrap succeeds, even before the first
                // foreground-app switch happens.
                HookHeartbeat.ping(systemContext, "bootstrap")
            }
        } catch (e: Exception) {
            log("$TAG: Failed to get system context: ${e.message}")
        }

        val completeResume: Method = activityRecordClass.getDeclaredMethod("completeResumeLocked")
        completeResume.isAccessible = true
        val module = ModuleMain.instance
        if (module == null) {
            log("$TAG: ModuleMain.instance is null, cannot install hook")
            return
        }

        module.hook(completeResume).intercept { chain ->
            val result = chain.proceed()
            try {
                val activityRecord = chain.thisObject ?: return@intercept result

                // packageName is enough to report; activity name is best-effort only.
                // Legacy XposedHelpers.getObjectField walked superclasses; plain
                // getDeclaredField does not (ActivityInfo.name lives on ComponentInfo).
                val packageName = getFieldOrNull(activityRecord, "packageName") as? String
                if (packageName.isNullOrBlank()) {
                    return@intercept result
                }

                val activityName =
                    runCatching {
                            val activityInfo = getFieldOrNull(activityRecord, "info")
                            activityInfo?.let { getFieldOrNull(it, "name") as? String }
                        }
                        .getOrNull()

                currentForegroundPackage = packageName
                currentForegroundActivity = activityName
                // Throttled internally — cheap to call on every switch.
                HookHeartbeat.ping(systemContext, packageName)

                if (packageName != lastForegroundPackage) {
                    lastForegroundPackage = packageName
                    val appName =
                        systemContext?.let { getAppDisplayName(it, packageName) } ?: packageName
                    val componentName =
                        if (activityName != null) "$packageName/$activityName/$appName"
                        else packageName
                    log("$TAG: Foreground app switched to: $componentName")
                    executeCustomOperations(packageName)
                }
            } catch (e: Throwable) {
                log("$TAG: Error in hook: ${e.message}")
            }
            result
        }

        log("$TAG: Successfully hooked into ActivityRecord.completeResumeLocked")
    }

    /**
     * Read an instance field by name, walking the superclass chain.
     *
     * Equivalent of legacy [de.robv.android.xposed.XposedHelpers.getObjectField]:
     * [Class.getDeclaredField] only searches the exact class, so inherited fields
     * such as [android.content.pm.ComponentInfo.name] on [android.content.pm.ActivityInfo]
     * would otherwise throw NoSuchFieldException.
     */
    private fun getFieldOrNull(target: Any, name: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    private fun loadConfiguration() {
        try {
            val sleepyConfig = ConfigManager.loadConfigFromXSharedPreferences(systemContext)
            cachedConfig =
                Config(
                    url = sleepyConfig.serverUrl,
                    secret = sleepyConfig.secret,
                    id = sleepyConfig.deviceId,
                    showName = sleepyConfig.showName,
                    enabled = sleepyConfig.enabled
                )
            if (isConfigUsable(cachedConfig)) {
                log(
                    "$TAG: Config loaded (enabled=${sleepyConfig.enabled}, url=${sleepyConfig.serverUrl})"
                )
            } else {
                log(
                    "$TAG: Config incomplete or empty after load — ${ConfigManager.describeLoadSources(systemContext)}"
                )
            }
        } catch (e: Exception) {
            log("$TAG: Failed to load configuration: ${e.message}")
        }
    }

    private fun executeCustomOperations(packageName: String) {
        val handlerInstance = handler ?: run {
            log("$TAG: Handler not initialized, skipping report")
            return
        }

        reportRunnable?.let { handlerInstance.removeCallbacks(it) }

        reportRunnable = Runnable {
            var config = cachedConfig
            if (config == null || !isConfigUsable(config)) {
                loadConfiguration()
                config = cachedConfig
            }
            if (config == null || !isConfigUsable(config)) {
                reportRunnable = null
                return@Runnable
            }
            if (!config.enabled) {
                reportRunnable = null
                return@Runnable
            }

            try {
                val appName = systemContext?.let { getAppDisplayName(it, packageName) } ?: packageName
                val batteryInfo = buildBatteryInfo()
                val statusText = "$appName$batteryInfo"

                SleepyApiClient.sendDeviceStatus(
                    baseUrl = config.url,
                    secret = config.secret,
                    id = config.id,
                    showName = config.showName,
                    using = true,
                    status = statusText,
                    callback =
                        object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                log("$TAG: Failed to send status: ${e.message}")
                            }

                            override fun onResponse(call: Call, response: Response) {
                                response.use {
                                    if (!response.isSuccessful) {
                                        log("$TAG: Server error: ${response.code}")
                                    }
                                }
                            }
                        }
                )
            } catch (e: Exception) {
                log("$TAG: Error in custom operations: ${e.message}")
            }

            reportRunnable = null
        }

        handlerInstance.postDelayed(reportRunnable!!, REPORT_DELAY_MS)
    }

    private fun isConfigUsable(config: Config?): Boolean {
        return config != null &&
            config.url.isNotBlank() &&
            config.secret.isNotBlank() &&
            config.id.isNotBlank() &&
            config.showName.isNotBlank()
    }

    private fun buildBatteryInfo(): String {
        return try {
            systemContext?.let { context ->
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val chargingStatus = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1
                val isCharging =
                    chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                        chargingStatus == BatteryManager.BATTERY_STATUS_FULL

                val batteryStr = if (battery in 0..100) "$battery%" else "-"
                val chargingIcon = if (isCharging) "⚡️" else "🔋"
                "[$batteryStr]$chargingIcon"
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun registerLockScreenReceiver() {
        if (lockReceiverRegistered) return

        val ctx = systemContext ?: return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }

            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val action = intent?.action
                        log("$TAG: $ACTION_LOG_PREFIX$action")

                        if (Intent.ACTION_SCREEN_OFF == action) {
                            val now = System.currentTimeMillis()
                            if (now - lastLockReportAt < LOCK_REPORT_COOLDOWN_MS) return
                            lastLockReportAt = now

                            handler?.post { sendLockScreenStatus() } ?: sendLockScreenStatus()
                        }
                    }
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }

            lockReceiverRegistered = true
            log("$TAG: Lock screen receiver registered")
        } catch (e: Exception) {
            log("$TAG: Failed to register lock receiver: ${e.message}")
        }
    }

    private fun sendLockScreenStatus() {
        var config = cachedConfig
        if (config == null || !isConfigUsable(config)) {
            loadConfiguration()
            config = cachedConfig
        }

        if (config == null || !isConfigUsable(config)) {
            log("$TAG: Lock report skipped, config unavailable")
            return
        }
        if (!config.enabled) {
            log("$TAG: Lock report skipped, config disabled")
            return
        }

        val batteryInfo = buildBatteryInfo()
        val statusText = "Screen locked$batteryInfo"

        try {
            SleepyApiClient.sendDeviceStatus(
                baseUrl = config.url,
                secret = config.secret,
                id = config.id,
                showName = config.showName,
                using = false,
                status = statusText,
                callback =
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            log("$TAG: Failed to send lock status: ${e.message}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.close()
                        }
                    }
            )
        } catch (e: Exception) {
            log("$TAG: Error sending lock status: ${e.message}")
        }
    }
}
