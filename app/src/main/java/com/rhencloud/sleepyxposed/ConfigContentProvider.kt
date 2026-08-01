package com.rhencloud.sleepyxposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * Exports module configuration to the system_server process, and receives liveness pings back
 * from it (see [call] / [HookHeartbeat]).
 *
 * Modern Android SELinux blocks system_server from reading another app's private
 * [android.content.SharedPreferences] / data dirs. Legacy [de.robv.android.xposed.XSharedPreferences]
 * also often fails under libxposed API 101+. A ContentProvider query from the system UID works
 * because the framework starts this app process and reads prefs with the app's own identity —
 * this is a normal Binder call, not a raw filesystem access, so it isn't subject to external
 * storage / SELinux write restrictions the way a system_server file write would be.
 *
 * [call] reuses that same proven channel for the opposite direction: system_server pings this
 * provider to prove a hook fired, and the app process (which hosts this provider) records that
 * itself, entirely inside its own sandbox — no cross-UID filesystem write required.
 *
 * Only the system UID (and this app) may query/call; other callers are rejected.
 */
class ConfigContentProvider : ContentProvider() {

  override fun onCreate(): Boolean = true

  override fun query(
          uri: Uri,
          projection: Array<out String>?,
          selection: String?,
          selectionArgs: Array<out String>?,
          sortOrder: String?
  ): Cursor? {
    enforceSystemOrSelf()
    val ctx = context ?: return null
    val config = ConfigManager.loadConfig(ctx)
    val cursor = MatrixCursor(COLUMNS)
    cursor.addRow(
            arrayOf<Any>(
                    config.serverUrl,
                    config.secret,
                    config.deviceId,
                    config.showName,
                    if (config.enabled) 1 else 0,
                    if (config.mediaEnabled) 1 else 0,
                    config.mediaDeviceId,
                    config.mediaShowName,
                    config.mediaMethod
            )
    )
    return cursor
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    if (method == METHOD_HEARTBEAT) {
      enforceSystemOrSelf()
      context?.let { HookHeartbeat.recordPing(it, arg.orEmpty(), extras) }
      return Bundle.EMPTY
    }
    return super.call(method, arg, extras)
  }

  override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.config"

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
          uri: Uri,
          values: ContentValues?,
          selection: String?,
          selectionArgs: Array<out String>?
  ): Int = 0

  private fun enforceSystemOrSelf() {
    val uid = Binder.getCallingUid()
    if (uid != Process.SYSTEM_UID && uid != Process.myUid() && uid != 0) {
      throw SecurityException("SleepyXposed config is only readable by system")
    }
  }

  companion object {
    const val AUTHORITY = "com.rhencloud.sleepyxposed.config"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")
    const val METHOD_HEARTBEAT = "heartbeat"
    const val EXTRA_FRAMEWORK_NAME = "framework_name"
    const val EXTRA_FRAMEWORK_VERSION = "framework_version"
    const val EXTRA_FRAMEWORK_VERSION_CODE = "framework_version_code"

    val COLUMNS =
            arrayOf(
                    "server_url",
                    "secret",
                    "device_id",
                    "show_name",
                    "enabled",
                    "media_enabled",
                    "media_device_id",
                    "media_show_name",
                    "media_method"
            )
  }
}
