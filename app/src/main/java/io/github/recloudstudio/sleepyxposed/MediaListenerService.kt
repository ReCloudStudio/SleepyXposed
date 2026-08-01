package io.github.recloudstudio.sleepyxposed

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.os.Handler
import android.os.HandlerThread
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * App-side fallback for capturing media playback status via the standard
 * `NotificationListenerService` API. Requires the user to grant "Notification access" once from
 * system settings (see [MainActivity]'s "grant notification access" button), but is the most
 * broadly compatible option across heavily customized ROMs where the system-hook based
 * [MediaStatusMonitor] may be unreliable.
 *
 * This service only actively reports when the configured (or auto-resolved) [MediaMethod] is
 * [MediaMethod.NOTIFICATION_LISTENER], so it stays silent when the user picked a different
 * method, avoiding duplicate reports with [MediaStatusMonitor].
 */
class MediaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SleepyXposed-MediaListener"
        private const val NOT_PLAYING_STATUS = "未在播放"
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var lastStatus: String? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            backgroundThread = HandlerThread(TAG).also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)

            mediaSessionManager =
                getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, MediaListenerService::class.java)

            // Drop any listener registered by a previous connection so we never leak it.
            sessionsChangedListener?.let {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(it)
            }

            val listener =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    handleControllers(controllers)
                }
            sessionsChangedListener = listener
            mediaSessionManager?.addOnActiveSessionsChangedListener(listener, componentName)
            handleControllers(mediaSessionManager?.getActiveSessions(componentName))
            Log.i(TAG, "Notification listener connected, media session watcher started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media session watcher: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        sessionsChangedListener?.let { mediaSessionManager?.removeOnActiveSessionsChangedListener(it) }
        sessionsChangedListener = null
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Media state changes are already observed via the active-sessions listener.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op
    }

    private fun handleControllers(controllers: List<MediaController>?) {
        backgroundHandler?.post { handleControllersOnBackground(controllers) }
    }

    private fun handleControllersOnBackground(controllers: List<MediaController>?) {
        val config = ConfigManager.loadConfig(this)
        if (!config.enabled || !config.mediaEnabled) return
        if (config.mediaDeviceId.isBlank() || config.mediaShowName.isBlank()) return
        if (config.serverUrl.isBlank() || config.secret.isBlank()) return

        val configuredMethod = MediaMethod.fromString(config.mediaMethod)
        val resolvedMethod = RomDetector.resolveMethod(applicationContext, configuredMethod)
        // Only this service should report when NOTIFICATION_LISTENER is the active method;
        // otherwise MediaStatusMonitor (system_server side) is responsible.
        if (resolvedMethod != MediaMethod.NOTIFICATION_LISTENER) return

        val playing =
            controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }

        val status: String
        val using: Boolean
        val metadata = playing?.metadata
        val title =
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        if (playing != null && title != null) {
            val artist =
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            status = if (artist.isNotBlank()) "♪$title - $artist" else "♪$title"
            using = true
        } else {
            status = NOT_PLAYING_STATUS
            using = false
        }

        // Only treat the status as delivered once the server confirms it; a failed send should
        // not suppress the next attempt of the same status.
        if (status == lastStatus) return

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
                            Log.w(TAG, "Failed to send media status: ${e.message}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                if (!response.isSuccessful) {
                                    Log.w(TAG, "Server error: ${response.code}")
                                } else {
                                    lastStatus = status
                                }
                            }
                        }
                    }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error sending media status: ${e.message}")
        }
    }
}
