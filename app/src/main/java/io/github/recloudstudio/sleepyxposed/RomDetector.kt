package io.github.recloudstudio.sleepyxposed

import android.content.Context
import android.os.Build

/**
 * Detects the current ROM / System UI vendor and recommends a [MediaMethod] for capturing media
 * playback status.
 *
 * Heavily customized ROMs (MIUI/HyperOS, ColorOS, FuntouchOS/OriginOS, EMUI/MagicUI, One UI,
 * Flyme, ...) are known to apply aggressive background/process restrictions that can make a
 * pure system-hook approach (querying `MediaSessionManager` from within system_server)
 * unreliable. For those, the standard `NotificationListenerService` API is recommended instead,
 * since it is the officially supported cross-ROM mechanism (at the cost of a one-time manual
 * permission grant). Near-stock/AOSP environments get the zero-setup system hook.
 */
object RomDetector {

    enum class RomFamily(val displayName: String) {
        MIUI("MIUI / HyperOS"),
        COLOR_OS("ColorOS / RealmeUI"),
        FUNTOUCH_OS("FuntouchOS / OriginOS"),
        EMUI("EMUI / MagicUI"),
        ONE_UI("One UI"),
        FLYME("Flyme"),
        STOCK("原生 / 接近 AOSP")
    }

    data class Recommendation(
        val method: MediaMethod,
        val rom: RomFamily,
        val androidVersion: String,
        val reason: String
    )

    // ROM identity and Android version are fixed for the process lifetime (a ROM/OS update
    // requires a reboot, which restarts every process that would hold this cache). Detection
    // involves several reflection + PackageManager calls, so memoizing avoids redoing that work
    // on every poll cycle (previously recomputed every 8s from MediaStatusMonitor).
    @Volatile private var cached: Recommendation? = null

    /** Recommend a concrete method (never [MediaMethod.AUTO] or [MediaMethod.DUMPSYS_SHELL]). */
    fun recommend(context: Context?): Recommendation {
        cached?.let { return it }

        val rom = detectRom(context)
        val versionName = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val recommendation =
            if (rom == RomFamily.STOCK) {
                Recommendation(
                    method = MediaMethod.SYSTEM_HOOK,
                    rom = rom,
                    androidVersion = versionName,
                    reason = "检测到接近原生的系统环境，系统钩子方式无需额外授权，兼容性最好"
                )
            } else {
                Recommendation(
                    method = MediaMethod.NOTIFICATION_LISTENER,
                    rom = rom,
                    androidVersion = versionName,
                    reason = "检测到定制系统「${rom.displayName}」，其后台管控可能影响系统钩子的稳定性，" +
                        "推荐使用通知监听方式（需在本应用内手动授权一次通知访问权限）"
                )
            }

        // Only memoize once we have enough signal to be confident: with a null context the
        // detection can only fall back to system properties and would conclude STOCK, which
        // would poison later contextual calls. A non-STOCK rom detected via properties alone is
        // definitive, so that's safe to cache too.
        if (context != null || rom != RomFamily.STOCK) {
            cached = recommendation
        }
        return recommendation
    }

    /** Resolve [MediaMethod.AUTO] to a concrete method; other values pass through unchanged. */
    fun resolveMethod(context: Context?, configured: MediaMethod): MediaMethod {
        return if (configured == MediaMethod.AUTO) recommend(context).method else configured
    }

    private fun detectRom(context: Context?): RomFamily {
        if (getSystemProperty("ro.miui.ui.version.name")?.isNotBlank() == true) return RomFamily.MIUI
        if (getSystemProperty("ro.mi.os.version.name")?.isNotBlank() == true) return RomFamily.MIUI
        if (getSystemProperty("ro.build.version.opporom")?.isNotBlank() == true) return RomFamily.COLOR_OS
        if (getSystemProperty("ro.build.version.oplusrom")?.isNotBlank() == true) return RomFamily.COLOR_OS
        if (getSystemProperty("ro.vivo.os.build.display.id")?.isNotBlank() == true) return RomFamily.FUNTOUCH_OS
        if (getSystemProperty("ro.vivo.os.version")?.isNotBlank() == true) return RomFamily.FUNTOUCH_OS
        if (getSystemProperty("ro.build.version.emui")?.isNotBlank() == true) return RomFamily.EMUI
        if (getSystemProperty("ro.build.version.magic")?.isNotBlank() == true) return RomFamily.EMUI
        if (getSystemProperty("ro.flyme.published")?.isNotBlank() == true) return RomFamily.FLYME
        if (getSystemProperty("ro.build.display.id")?.contains("flyme", ignoreCase = true) == true) {
            return RomFamily.FLYME
        }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi") || brand.contains("poco") -> return RomFamily.MIUI
            manufacturer.contains("oppo") || brand.contains("oppo") ||
                brand.contains("realme") || brand.contains("oneplus") -> return RomFamily.COLOR_OS
            manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo") ->
                return RomFamily.FUNTOUCH_OS
            manufacturer.contains("huawei") || brand.contains("huawei") || brand.contains("honor") ->
                return RomFamily.EMUI
            manufacturer.contains("samsung") -> return RomFamily.ONE_UI
            manufacturer.contains("meizu") -> return RomFamily.FLYME
        }

        if (context != null) {
            if (hasPackage(context, "com.miui.securitycenter")) return RomFamily.MIUI
            if (hasPackage(context, "com.coloros.safecenter")) return RomFamily.COLOR_OS
            if (hasPackage(context, "com.vivo.abe")) return RomFamily.FUNTOUCH_OS
            if (hasPackage(context, "com.huawei.systemmanager")) return RomFamily.EMUI
        }

        return RomFamily.STOCK
    }

    private fun hasPackage(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Reads a system property via reflection; works from both app and system_server contexts. */
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (_: Exception) {
            null
        }
    }
}
