package io.github.recloudstudio.sleepyxposed

/**
 * Strategies for acquiring the current media playback status.
 *
 * - [AUTO]: let the module decide at runtime, based on [RomDetector]'s recommendation for the
 *   current Android version / System UI vendor. Resolves to either [SYSTEM_HOOK] or
 *   [NOTIFICATION_LISTENER].
 * - [SYSTEM_HOOK]: query `MediaSessionManager` directly from the hooked system_server process.
 *   Zero extra setup, but depends on the Xposed hook staying active and the ROM not restricting
 *   system-side session queries.
 * - [NOTIFICATION_LISTENER]: run a `NotificationListenerService` inside the app process itself.
 *   Requires the user to grant "Notification access" once, but is the most broadly compatible
 *   option across heavily customized ROMs.
 * - [DUMPSYS_SHELL]: shell out to `dumpsys media_session` and parse its text output, mirroring
 *   the approach used by Magisk-based Sleepy scripts. Kept as a manual fallback/debug option.
 */
enum class MediaMethod {
    AUTO,
    SYSTEM_HOOK,
    NOTIFICATION_LISTENER,
    DUMPSYS_SHELL;

    companion object {
        fun fromString(value: String?): MediaMethod = entries.find { it.name == value } ?: AUTO
    }
}
