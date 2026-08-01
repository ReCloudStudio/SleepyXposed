package com.rhencloud.sleepyxposed

import android.os.SystemClock
import java.io.File

/**
 * Liveness signal written by the system_server-side hooks and read by the app UI to answer
 * "is the Xposed hook actually running right now".
 *
 * A naive in-process activation probe cannot answer this: with the module's default LSPosed scope of `android`
 * (system_server only), the app's own process is never touched by a hook, so a static
 * in-process probe method has nothing to flip it to `true` and always reads `false` — which is
 * exactly the "一直显示未检测到" (always shows "not detected") symptom, even while the
 * system_server hook is working and reporting normally.
 *
 * Instead, [ForegroundAppMonitor] and [MediaStatusMonitor] each call [touch] whenever they
 * demonstrably run inside system_server (hook installed, foreground switch observed, poll
 * executed). That timestamp is written to a small file in the same public directory already used
 * for [ConfigManager]'s JSON config mirror — a location already proven reachable from both
 * system_server and the app process. The app UI then just checks whether that timestamp is
 * recent via [isRecentlyActive].
 */
object HookHeartbeat {
    private const val FILE_NAME = "heartbeat.txt"

    /** Don't write on every single call (e.g. every foreground-app switch) — only this often. */
    private const val WRITE_THROTTLE_MS = 30_000L

    /** UI treats the hook as active if it has heard from it within this window. */
    private const val FRESHNESS_WINDOW_MS = 90_000L

    @Volatile private var lastWriteAtElapsed: Long = 0L

    private fun file(): File = File(ConfigManager.getPrimaryPublicDir(), FILE_NAME)

    /** Call from system_server-side code whenever a hook demonstrably executes. Cheap: throttled. */
    fun touch(detail: String = "") {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWriteAtElapsed < WRITE_THROTTLE_MS) return
        lastWriteAtElapsed = now

        try {
            val f = file()
            f.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val body = System.currentTimeMillis().toString()
            f.writeText(if (detail.isNotBlank()) "$body\n$detail" else body)
            f.setReadable(true, false)
        } catch (_: Exception) {
            // Best-effort; UI simply keeps showing "not detected" if this never lands.
        }
    }

    /** Call from the app UI process. */
    fun isRecentlyActive(): Boolean {
        val ago = lastSeenMillisAgo() ?: return false
        return ago < FRESHNESS_WINDOW_MS
    }

    /** Milliseconds since the last heartbeat, or null if none has ever been recorded. */
    fun lastSeenMillisAgo(): Long? {
        return try {
            val f = file()
            if (!f.exists()) return null
            val ts = f.readText().lineSequence().firstOrNull()?.trim()?.toLongOrNull() ?: return null
            (System.currentTimeMillis() - ts).coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }
}
