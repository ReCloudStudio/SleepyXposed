package io.github.recloudstudio.sleepyxposed

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Monitors media playback status from within the system_server process and reports it to the
 * Sleepy server. Handles the two "system side" acquisition strategies:
 *
 * - [MediaMethod.SYSTEM_HOOK]: uses [MediaSessionManager.getActiveSessions] directly. This works
 *   without a registered notification-listener component because the call executes with the
 *   system process's own identity, which implicitly holds `MEDIA_CONTENT_CONTROL`.
 * - [MediaMethod.DUMPSYS_SHELL]: shells out to `dumpsys media_session` and parses its text
 *   output, mirroring the approach used by Magisk-based Sleepy scripts. Kept as a fallback for
 *   ROMs whose `MediaSessionManager` implementation has been modified or restricted.
 *
 * [MediaMethod.NOTIFICATION_LISTENER] is intentionally NOT handled here; it is served by
 * [MediaListenerService] running inside the app's own process. Both sides resolve
 * [MediaMethod.AUTO] independently via [RomDetector.resolveMethod], so exactly one of them ends
 * up actively reporting for a given device.
 */
class MediaStatusMonitor(private val log: (String) -> Unit) {

    companion object {
        private const val TAG = "SleepyXposed-Media"
        private const val POLL_INTERVAL_MS = 8_000L
        private const val DUMPSYS_TIMEOUT_SECONDS = 5L
        private val DUMPSYS_DESCRIPTION_REGEX = Regex("description=([^,]+),\\s*([^,]+)")
        private const val NOT_PLAYING_STATUS = "未在播放"
    }

    private data class MediaInfo(val title: String, val artist: String)

    private var handler: Handler? = null
    private var systemContext: Context? = null
    private var pollRunnable: Runnable? = null
    private var lastStatus: String? = null
    private var lastSkipReason: String? = null
    private var mediaSessionManager: MediaSessionManager? = null

    fun initializeForSystemServer(classLoader: ClassLoader) {
        log("$TAG: Initializing media status monitor...")
        try {
            val activityThreadClass =
                Class.forName("android.app.ActivityThread", false, classLoader)
            val currentActivityThread =
                activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val getSystemContext = activityThreadClass.getMethod("getSystemContext")
            systemContext = getSystemContext.invoke(currentActivityThread) as? Context

            val context = systemContext
            if (context == null) {
                log("$TAG: Failed to obtain system context, media monitor disabled")
                return
            }

            // Never run polling on the system_server main thread: both the MediaSessionManager
            // query and the blocking dumpsys child process could stall it and trigger a Watchdog
            // soft reboot. A dedicated HandlerThread keeps this work off the main looper.
            val thread = HandlerThread("$TAG-poll").also { it.start() }
            handler = Handler(thread.looper)
            schedulePoll(POLL_INTERVAL_MS)
            log("$TAG: Media status monitor initialized")
        } catch (e: Exception) {
            log("$TAG: Failed to initialize media monitor: ${e.message}")
        }
    }

    private fun schedulePoll(delay: Long) {
        val currentHandler = handler ?: return
        pollRunnable?.let { currentHandler.removeCallbacks(it) }
        val runnable = Runnable {
            try {
                poll()
            } catch (e: Exception) {
                log("$TAG: Poll error: ${e.message}")
            }
            schedulePoll(POLL_INTERVAL_MS)
        }
        pollRunnable = runnable
        currentHandler.postDelayed(runnable, delay)
    }

    private fun poll() {
        // Throttled internally — proves the media hook side is alive regardless of whether
        // media reporting itself is enabled/configured below.
        HookHeartbeat.ping(systemContext)

        val config = ConfigManager.loadConfigFromXSharedPreferences(systemContext)
        if (!config.enabled) {
            return
        }
        if (!config.mediaEnabled) {
            return
        }
        if (config.mediaDeviceId.isBlank() || config.mediaShowName.isBlank()) {
            logSkipOnce("media device id / show name empty") { "media device id / show name empty" }
            return
        }
        if (config.serverUrl.isBlank() || config.secret.isBlank()) {
            logSkipOnce("server url / secret empty") {
                "server url / secret empty — ${ConfigManager.describeLoadSources(systemContext)}"
            }
            return
        }
        lastSkipReason = null

        val configuredMethod = MediaMethod.fromString(config.mediaMethod)
        val resolvedMethod = RomDetector.resolveMethod(systemContext, configuredMethod)

        // NOTIFICATION_LISTENER is handled by the app-process service, not here.
        if (resolvedMethod != MediaMethod.SYSTEM_HOOK && resolvedMethod != MediaMethod.DUMPSYS_SHELL) {
            return
        }

        val info =
            if (resolvedMethod == MediaMethod.DUMPSYS_SHELL) readViaDumpsys() else readViaMediaSessionManager()

        val status: String
        val using: Boolean
        if (info != null) {
            status = if (info.artist.isNotBlank()) "♪${info.title} - ${info.artist}" else "♪${info.title}"
            using = true
        } else {
            status = NOT_PLAYING_STATUS
            using = false
        }

        // Only treat the status as delivered once the server confirms it; a failed send should
        // not suppress the next poll of the same status.
        if (status == lastStatus) return
        log("$TAG: Media status changed via $resolvedMethod: $status")

        try {
            SleepyApiClient.sendDeviceStatus(
                baseUrl = config.serverUrl,
                secret = config.secret,
                id = config.mediaDeviceId,
                showName = config.mediaShowName,
                using = using,
                status = status,
                callback =
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            log("$TAG: Failed to send media status: ${e.message}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                if (!response.isSuccessful) {
                                    log("$TAG: Server error: ${response.code}")
                                } else {
                                    lastStatus = status
                                }
                            }
                        }
                    }
            )
        } catch (e: Exception) {
            log("$TAG: Error sending media status: ${e.message}")
        }
    }

    private fun logSkipOnce(reasonKey: String, message: () -> String) {
        if (lastSkipReason == reasonKey) return
        lastSkipReason = reasonKey
        log("$TAG: Media report skipped: ${message()}")
    }

    private fun readViaMediaSessionManager(): MediaInfo? {
        val context = systemContext ?: return null
        return try {
            val manager =
                mediaSessionManager
                    ?: (context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
                        ?.also { mediaSessionManager = it }
                    ?: return null
            val sessions = manager.getActiveSessions(null)
            val playing =
                sessions.firstOrNull { controller ->
                    controller.playbackState?.state == PlaybackState.STATE_PLAYING
                } ?: return null

            val metadata = playing.metadata ?: return null
            val title =
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: return null
            val artist =
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            MediaInfo(title, artist)
        } catch (e: SecurityException) {
            log("$TAG: MediaSessionManager access denied: ${e.message}")
            null
        } catch (e: Exception) {
            log("$TAG: MediaSessionManager read failed: ${e.message}")
            null
        }
    }

    private fun readViaDumpsys(): MediaInfo? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("dumpsys", "media_session").redirectErrorStream(true).start()

            // Drain stdout on a separate thread WHILE waiting for exit, not after: dumpsys
            // output can exceed the OS pipe buffer (a few KB) once several media sessions are
            // active, and calling waitFor() first (as an earlier version did) would then
            // deadlock — the child blocks writing to a full pipe nobody is reading, so
            // waitFor() always hits the timeout and every poll silently fails.
            val outputRef = StringBuilder()
            val reader = process.inputStream
            val readerThread =
                Thread {
                        try {
                            BufferedReader(InputStreamReader(reader)).use { br ->
                                val buf = CharArray(4096)
                                while (true) {
                                    val n = br.read(buf)
                                    if (n < 0) break
                                    outputRef.append(buf, 0, n)
                                }
                            }
                        } catch (_: IOException) {
                            // Stream closed because we destroyed the process on timeout; ignore.
                        }
                    }
                    .also { it.isDaemon = true; it.start() }

            // Bounded wait so a hung dumpsys can never block the (system_server) caller; the
            // process is destroyed in the finally block on the way out.
            if (!process.waitFor(DUMPSYS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log("$TAG: dumpsys media_session timed out")
                readerThread.join(200)
                return null
            }
            readerThread.join(TimeUnit.SECONDS.toMillis(DUMPSYS_TIMEOUT_SECONDS))
            val output = outputRef.toString()

            // dumpsys prints one block per media session; the first description= in the whole
            // dump may belong to a paused session. Select only the block that is actually
            // PLAYING and extract its description, so we never report the wrong track.
            val playingBlock =
                output
                    .split(Regex("(?m)^\\s*#\\d+:\\s*MediaSession"))
                    .firstOrNull { block ->
                        block.contains("state=PLAYING") || block.contains("state=3")
                    }
                    ?: return null

            val match = DUMPSYS_DESCRIPTION_REGEX.find(playingBlock) ?: return null
            val title = match.groupValues[1].trim()
            val artist = match.groupValues[2].trim().let { if (it == "null") "" else it }
            if (title.isBlank()) null else MediaInfo(title, artist)
        } catch (e: Exception) {
            log("$TAG: dumpsys read failed: ${e.message}")
            null
        } finally {
            process?.destroy()
        }
    }
}
