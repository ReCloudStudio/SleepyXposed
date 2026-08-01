package io.github.recloudstudio.sleepyxposed

import android.content.Context
import android.os.Bundle
import android.os.SystemClock

/**
 * Liveness signal written by the system_server-side hooks and read by the app UI to answer
 * "is the Xposed hook actually running right now".
 *
 * A naive in-process activation probe cannot answer this: with the module's default LSPosed
 * scope of `android` (system_server only), the app's own process is never touched by a hook, so
 * a static in-process probe method has nothing to flip it to `true` and always reads `false`.
 *
 * A first version of this fix had system_server write a small file directly onto external
 * storage. That looked right (the same directory is already used for [ConfigManager]'s JSON
 * config mirror, which system_server reads fine) but silently failed: the directory was only
 * ever `chmod`-ed readable+executable for other UIDs, never writable, and even with correct bits
 * modern external storage is FUSE-emulated, where cross-UID *writes* into another app's
 * package-specific directory aren't reliably honored by raw POSIX permissions the way *reads*
 * are. Reads kept working (hence config/status reporting was fine); writes from system_server
 * never landed, so the heartbeat file was never created.
 *
 * This version instead reuses [ConfigContentProvider]'s Binder channel — the same one config
 * queries already go through successfully — by calling it with [ConfigContentProvider.METHOD_HEARTBEAT].
 * The provider runs *inside the app's own process*, so [recordPing] just writes to the app's own
 * private [android.content.SharedPreferences]: no cross-UID filesystem access at all, so nothing
 * for storage sandboxing to block.
 */
object HookHeartbeat {
    private const val PREFS_NAME = "sleepy_heartbeat"
    private const val KEY_LAST_SEEN_MS = "last_seen_ms"
    private const val KEY_LAST_DETAIL = "last_detail"
    private const val KEY_FRAMEWORK_NAME = "framework_name"
    private const val KEY_FRAMEWORK_VERSION = "framework_version"
    private const val KEY_FRAMEWORK_VERSION_CODE = "framework_version_code"

    /** Don't ping on every single call (e.g. every foreground-app switch) — only this often. */
    private const val PING_THROTTLE_MS = 30_000L

    /** UI treats the hook as active if it has heard from it within this window. */
    private const val FRESHNESS_WINDOW_MS = 90_000L

    @Volatile private var lastPingAtElapsed: Long = 0L

    data class FrameworkInfo(val name: String, val version: String, val versionCode: Long)

    /**
     * Call from system_server-side code (via [systemContext]) whenever a hook demonstrably
     * executes. Cheap: throttled, and the actual write happens in the app process via IPC.
     *
     * Opportunistically piggybacks the *actual* running framework's name/version (read straight
     * from the libxposed `XposedInterface` this module is attached to, via [ModuleMain.instance])
     * onto the same call. An earlier version pushed framework info exactly once, right when the
     * hook first bootstraps — but if that single attempt lost the race (e.g. ran before the
     * device's first unlock, when the app process/private storage may not be available yet), it
     * was never retried and stayed stuck on "unknown" until the next reboot, unlike the heartbeat
     * itself which self-heals because it's retried on every foreground switch / poll. Sending it
     * with every (throttled) ping gives it the same automatic retry behavior.
     */
    fun ping(systemContext: Context?, detail: String = "") {
        if (systemContext == null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPingAtElapsed < PING_THROTTLE_MS) return
        lastPingAtElapsed = now

        try {
            val extras = Bundle()
            try {
                val module = ModuleMain.instance
                if (module != null) {
                    extras.putString(ConfigContentProvider.EXTRA_FRAMEWORK_NAME, module.getFrameworkName())
                    extras.putString(ConfigContentProvider.EXTRA_FRAMEWORK_VERSION, module.getFrameworkVersion())
                    extras.putLong(
                        ConfigContentProvider.EXTRA_FRAMEWORK_VERSION_CODE,
                        module.getFrameworkVersionCode()
                    )
                }
            } catch (_: Exception) {
                // Framework info is a bonus; the heartbeat ping itself still proceeds without it.
            }

            systemContext.contentResolver.call(
                ConfigContentProvider.CONTENT_URI,
                ConfigContentProvider.METHOD_HEARTBEAT,
                detail,
                extras
            )
        } catch (_: Exception) {
            // Best-effort; UI simply keeps showing "not detected" if this never lands.
        }
    }

    /** Called by [ConfigContentProvider.call] inside the app process — plain private prefs. */
    fun recordPing(appContext: Context, detail: String, extras: Bundle? = null) {
        try {
            appContext
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SEEN_MS, System.currentTimeMillis())
                .putString(KEY_LAST_DETAIL, detail)
                .apply()
        } catch (_: Exception) {
            // Best-effort.
        }

        val name = extras?.getString(ConfigContentProvider.EXTRA_FRAMEWORK_NAME)
        if (!name.isNullOrBlank()) {
            recordFrameworkInfo(
                appContext,
                name = name,
                version = extras.getString(ConfigContentProvider.EXTRA_FRAMEWORK_VERSION).orEmpty(),
                versionCode = extras.getLong(ConfigContentProvider.EXTRA_FRAMEWORK_VERSION_CODE, 0L)
            )
        }
    }

    /** Called by [ConfigContentProvider.call] inside the app process — plain private prefs. */
    fun recordFrameworkInfo(appContext: Context, name: String, version: String, versionCode: Long) {
        if (name.isBlank()) return
        try {
            appContext
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FRAMEWORK_NAME, name)
                .putString(KEY_FRAMEWORK_VERSION, version)
                .putLong(KEY_FRAMEWORK_VERSION_CODE, versionCode)
                .apply()
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    /** Call from the app UI process. Null until the hook has pushed framework info at least once. */
    fun frameworkInfo(context: Context): FrameworkInfo? {
        return try {
            val prefs =
                context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_FRAMEWORK_NAME, null)
            if (name.isNullOrBlank()) return null
            FrameworkInfo(
                name = name,
                version = prefs.getString(KEY_FRAMEWORK_VERSION, "").orEmpty(),
                versionCode = prefs.getLong(KEY_FRAMEWORK_VERSION_CODE, 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Call from the app UI process. */
    fun isRecentlyActive(context: Context): Boolean {
        val ago = lastSeenMillisAgo(context) ?: return false
        return ago < FRESHNESS_WINDOW_MS
    }

    /** Milliseconds since the last heartbeat, or null if none has ever been recorded. */
    fun lastSeenMillisAgo(context: Context): Long? {
        return try {
            val prefs =
                context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ts = prefs.getLong(KEY_LAST_SEEN_MS, 0L)
            if (ts <= 0L) null else (System.currentTimeMillis() - ts).coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }
}
