package io.github.recloudstudio.sleepyxposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import java.io.IOException
import java.lang.reflect.Method
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

class ForegroundAppMonitor(private val log: (String) -> Unit) {

    companion object {
        private const val TAG = "SleepyXposed"
        private const val REPORT_DELAY_MS = 1000L
        // PackageManager.MATCH_ANY_USER. Was 0x00002000 (actually MATCH_UNINSTALLED_PACKAGES),
        // which silently broke app-label lookups for packages running under a different
        // Android user (work profile / multi-user devices) on API 33+: getApplicationInfo()
        // would throw NameNotFoundException and getAppDisplayName() would fall back to the
        // raw package name instead of the human-readable label.
        private const val MATCH_ANY_USER_FLAG = 0x00400000
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
    private var workerThread: HandlerThread? = null
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
                // Dedicated worker thread — deliberately NOT systemContext.mainLooper.
                // completeResumeLocked (hooked below) runs with ActivityTaskManagerService's
                // global WindowManagerGlobalLock held, and that lock gates virtually all
                // window/input/activity-lifecycle processing. Anything that can block —
                // Binder IPC to our app's ConfigContentProvider (which can even trigger a
                // synchronous cold start of a frozen/killed app process), or file I/O — must
                // never run synchronously inside the hook, and must never be posted to the
                // real system_server main Looper either. Everything of that kind is
                // dispatched to this thread instead, so a slow/frozen app process can only
                // ever stall our own worker thread, never the WM lock or the UI/input path
                // (a stall of either is what triggers the Watchdog and forces a full reboot).
                workerThread = HandlerThread("$TAG-worker").also { it.start() }
                handler = Handler(workerThread!!.looper)
                handler?.post {
                    loadConfiguration()
                    registerLockScreenReceiver()
                    // Prove the hook is alive as soon as bootstrap succeeds, even before the
                    // first foreground-app switch happens. Framework name/version rides along
                    // on every ping too (see HookHeartbeat.ping), so it gets the same
                    // automatic retry.
                    HookHeartbeat.ping(systemContext, "bootstrap")
                }
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
                // Both reads are plain in-memory field access — no IPC, no I/O — so they're
                // safe to do synchronously here, still under the WindowManagerGlobalLock.
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

                // Everything from here on can block — HookHeartbeat.ping() and
                // getAppDisplayName()/executeCustomOperations() may reach across processes to
                // our own app (Binder IPC, possibly a synchronous cold start if that process
                // is frozen or was killed) or hit disk. None of it may run on this thread
                // while the WindowManagerGlobalLock is held, so hand it off to the worker
                // thread and return immediately.
                val handlerInstance = handler
                if (handlerInstance == null) {
                    log("$TAG: Handler not initialized, skipping post-resume work")
                    return@intercept result
                }
                handlerInstance.post {
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
                }
            } catch (e: Throwable) {
                log("$TAG: Error in hook: ${e.message}")
            }
            result
        }

        log("$TAG: Successfully hooked into ActivityRecord.completeResumeLocked")
    }

    /**
     * Cache of resolved [java.lang.reflect.Field]s, keyed by "ClassName#fieldName". Populated
     * lazily by [getFieldOrNull]. `completeResumeLocked` fires on every activity resume
     * system-wide — a genuinely hot path — so re-doing [Class.getDeclaredField] (a linear scan)
     * plus [java.lang.reflect.Field.setAccessible] (a security check) on every single call would
     * be wasted CPU: the resolved Field for a given (class, name) pair never changes for the
     * process's lifetime, so it only needs to be looked up once.
     */
    private val fieldCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field?>()

    /**
     * Read an instance field by name, walking the superclass chain.
     *
     * Equivalent of legacy [de.robv.android.xposed.XposedHelpers.getObjectField]:
     * [Class.getDeclaredField] only searches the exact class, so inherited fields
     * such as [android.content.pm.ComponentInfo.name] on [android.content.pm.ActivityInfo]
     * would otherwise throw NoSuchFieldException.
     */
    private fun getFieldOrNull(target: Any, name: String): Any? {
        val targetClass = target.javaClass
        val cacheKey = "${targetClass.name}#$name"
        // computeIfAbsent caches misses too (a null value), so a field that's genuinely absent
        // only ever walks the superclass chain once rather than on every hook invocation.
        val field =
            fieldCache.computeIfAbsent(cacheKey) {
                var clazz: Class<*>? = targetClass
                var resolved: java.lang.reflect.Field? = null
                while (clazz != null) {
                    resolved =
                        try {
                            clazz.getDeclaredField(name).also { it.isAccessible = true }
                        } catch (_: NoSuchFieldException) {
                            null
                        }
                    if (resolved != null) break
                    clazz = clazz.superclass
                }
                resolved
            }
        return field?.get(target)
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
                // Explicit `handler` (our worker thread) so onReceive itself never runs on
                // system_server's default main thread either — same reasoning as the
                // completeResumeLocked hook above, just cheaper insurance here since
                // onReceive's own work is already trivial.
                ctx.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter, null, handler)
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
