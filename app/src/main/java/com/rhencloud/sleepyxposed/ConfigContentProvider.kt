package com.rhencloud.sleepyxposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process

/**
 * Exports module configuration to the system_server process.
 *
 * Modern Android SELinux blocks system_server from reading another app's private
 * [android.content.SharedPreferences] / data dirs. Legacy [de.robv.android.xposed.XSharedPreferences]
 * also often fails under libxposed API 101. A ContentProvider query from the system UID works
 * because the framework starts this app process and reads prefs with the app's own identity.
 *
 * Only the system UID (and this app) may query; other callers are rejected.
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
