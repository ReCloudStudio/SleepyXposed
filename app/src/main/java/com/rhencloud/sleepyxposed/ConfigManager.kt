package com.rhencloud.sleepyxposed

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import java.io.File
import org.json.JSONObject

/** Configuration data class */
data class SleepyConfig(
        val serverUrl: String = "",
        val secret: String = "",
        val deviceId: String = "",
        val showName: String = "",
        val enabled: Boolean = false,
        /** Whether media playback status reporting is enabled. */
        val mediaEnabled: Boolean = false,
        /** Device ID used when reporting media playback status (independent of [deviceId]). */
        val mediaDeviceId: String = "",
        /** Display name used when reporting media playback status. */
        val mediaShowName: String = "",
        /** Name of the [MediaMethod] used to acquire media playback status. */
        val mediaMethod: String = MediaMethod.AUTO.name
) {
  fun hasRequiredFields(): Boolean {
    return serverUrl.isNotBlank() && secret.isNotBlank() && deviceId.isNotBlank() && showName.isNotBlank()
  }
}

/** Configuration manager for loading and saving config across app + system_server. */
object ConfigManager {
  private const val PREF_FILE_NAME = "sleepy_config"
  private const val MODULE_PACKAGE_NAME = "com.rhencloud.sleepyxposed"
  private const val KEY_SERVER_URL = "server_url"
  private const val KEY_SECRET = "secret"
  private const val KEY_DEVICE_ID = "device_id"
  private const val KEY_SHOW_NAME = "show_name"
  private const val KEY_ENABLED = "enabled"
  private const val KEY_MEDIA_ENABLED = "media_enabled"
  private const val KEY_MEDIA_DEVICE_ID = "media_device_id"
  private const val KEY_MEDIA_SHOW_NAME = "media_show_name"
  private const val KEY_MEDIA_METHOD = "media_method"
  private const val FALLBACK_DIR = "SleepyXposed"
  private const val FALLBACK_FILE_NAME = "config.json"

  // Hooked-process config reads happen on a hot path (MediaStatusMonitor polls every few
  // seconds). Config only changes when the user hits Save, so memoize it for a short window
  // instead of re-running a ContentProvider IPC + filesystem fallback chain on every poll.
  private const val SYSTEM_CACHE_TTL_MS = 15_000L
  @Volatile private var cachedSystemConfig: SleepyConfig? = null
  @Volatile private var cachedSystemConfigAt: Long = 0L

  /** Load configuration for module app process */
  fun loadConfig(context: Context): SleepyConfig {
    return try {
      val de = readConfigFromPrefs(getDeviceProtectedContext(context), requireComplete = false)
      if (de != null && de.hasRequiredFields()) return de

      val ce = readConfigFromPrefs(context, requireComplete = false)
      if (ce != null && ce.hasRequiredFields()) return ce

      loadConfigFromJsonFiles(context) ?: de ?: ce ?: SleepyConfig()
    } catch (_: Exception) {
      loadConfigFromJsonFiles(context) ?: SleepyConfig()
    }
  }

  /**
   * Load configuration inside hooked processes (typically system_server), with a short-lived
   * cache (see [SYSTEM_CACHE_TTL_MS]).
   *
   * Private app data is SELinux-blocked from system_server on modern ROMs, so the primary path
   * is a [ConfigContentProvider] query (app process reads its own prefs on the system's behalf),
   * with a public JSON file as fallback for the rare case a provider query can't be made.
   */
  fun loadConfigFromXSharedPreferences(systemContext: Context? = null, forceRefresh: Boolean = false): SleepyConfig {
    val now = SystemClock.elapsedRealtime()
    val cached = cachedSystemConfig
    if (!forceRefresh && cached != null && now - cachedSystemConfigAt < SYSTEM_CACHE_TTL_MS) {
      return cached
    }

    val fresh = loadConfigFromXSharedPreferencesUncached(systemContext)
    cachedSystemConfig = fresh
    cachedSystemConfigAt = now
    return fresh
  }

  private fun loadConfigFromXSharedPreferencesUncached(systemContext: Context?): SleepyConfig {
    systemContext?.let { ctx ->
      loadViaContentProvider(ctx)?.takeIf { it.hasRequiredFields() }?.let {
        return it
      }
    }

    loadConfigFromJsonFiles(null)?.takeIf { it.hasRequiredFields() }?.let {
      return it
    }

    return loadViaContentProvider(systemContext) ?: loadConfigFromJsonFiles(null) ?: SleepyConfig()
  }

  /** Human-readable diagnostics for why system_server cannot see config. Diagnostic-only. */
  fun describeLoadSources(systemContext: Context?): String {
    val provider = runCatching { loadViaContentProvider(systemContext) }.getOrNull()
    val json = runCatching { loadConfigFromJsonFiles(null) }.getOrNull()
    val existing =
            getAllJsonCandidates(null).filter { it.exists() }.joinToString(",") { it.absolutePath }
    return "provider=${provider?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}; " +
            "json=${json?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}; " +
            "jsonFiles=[${existing.ifBlank { "none" }}]"
  }

  private fun loadViaContentProvider(context: Context?): SleepyConfig? {
    if (context == null) return null
    return try {
      context.contentResolver
              .query(ConfigContentProvider.CONTENT_URI, null, null, null, null)
              ?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                fun col(name: String): Int = cursor.getColumnIndex(name)
                fun str(name: String): String {
                  val i = col(name)
                  return if (i >= 0 && !cursor.isNull(i)) cursor.getString(i) ?: "" else ""
                }
                fun bool(name: String): Boolean {
                  val i = col(name)
                  return if (i >= 0 && !cursor.isNull(i)) cursor.getInt(i) != 0 else false
                }
                SleepyConfig(
                        serverUrl = str("server_url"),
                        secret = str("secret"),
                        deviceId = str("device_id"),
                        showName = str("show_name"),
                        enabled = bool("enabled"),
                        mediaEnabled = bool("media_enabled"),
                        mediaDeviceId = str("media_device_id"),
                        mediaShowName = str("media_show_name"),
                        mediaMethod = str("media_method").ifBlank { MediaMethod.AUTO.name }
                )
              }
    } catch (_: Exception) {
      null
    }
  }

  /** Save configuration in module app process */
  fun saveConfig(context: Context, config: SleepyConfig): Boolean {
    val deSaved = writeConfigToPrefs(getDeviceProtectedContext(context), config)
    val ceSaved = writeConfigToPrefs(context, config)
    // JSON mirror for system_server (private app data is SELinux-blocked from system).
    val jsonSaved = saveConfigToJsonFiles(context, config)

    // Invalidate the system-side cache immediately so a Save takes effect without waiting out
    // the TTL window.
    cachedSystemConfig = null
    cachedSystemConfigAt = 0L

    try {
      context.contentResolver.notifyChange(ConfigContentProvider.CONTENT_URI, null)
    } catch (_: Exception) {}

    return deSaved || ceSaved || jsonSaved
  }

  private fun readConfigFromPrefs(
          context: Context,
          requireComplete: Boolean = true
  ): SleepyConfig? {
    return try {
      val pref = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
      if (!pref.contains(KEY_SERVER_URL) &&
                      !pref.contains(KEY_SECRET) &&
                      !pref.contains(KEY_DEVICE_ID) &&
                      !pref.contains(KEY_SHOW_NAME) &&
                      !pref.contains(KEY_MEDIA_ENABLED)
      ) {
        return null
      }

      val config =
              SleepyConfig(
                      serverUrl = pref.getString(KEY_SERVER_URL, "") ?: "",
                      secret = pref.getString(KEY_SECRET, "") ?: "",
                      deviceId = pref.getString(KEY_DEVICE_ID, "") ?: "",
                      showName = pref.getString(KEY_SHOW_NAME, "") ?: "",
                      enabled = pref.getBoolean(KEY_ENABLED, false),
                      mediaEnabled = pref.getBoolean(KEY_MEDIA_ENABLED, false),
                      mediaDeviceId = pref.getString(KEY_MEDIA_DEVICE_ID, "") ?: "",
                      mediaShowName = pref.getString(KEY_MEDIA_SHOW_NAME, "") ?: "",
                      mediaMethod =
                              pref.getString(KEY_MEDIA_METHOD, MediaMethod.AUTO.name)
                                      ?: MediaMethod.AUTO.name
              )
      if (requireComplete && !config.hasRequiredFields()) null else config
    } catch (_: Exception) {
      null
    }
  }

  private fun writeConfigToPrefs(context: Context, config: SleepyConfig): Boolean {
    return try {
      val pref = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
      val saved =
              pref.edit()
                      .putString(KEY_SERVER_URL, config.serverUrl)
                      .putString(KEY_SECRET, config.secret)
                      .putString(KEY_DEVICE_ID, config.deviceId)
                      .putString(KEY_SHOW_NAME, config.showName)
                      .putBoolean(KEY_ENABLED, config.enabled)
                      .putBoolean(KEY_MEDIA_ENABLED, config.mediaEnabled)
                      .putString(KEY_MEDIA_DEVICE_ID, config.mediaDeviceId)
                      .putString(KEY_MEDIA_SHOW_NAME, config.mediaShowName)
                      .putString(KEY_MEDIA_METHOD, config.mediaMethod)
                      .commit()
      makePrefsWorldReadable(context)
      saved
    } catch (_: Exception) {
      false
    }
  }

  fun getConfigFilePath(context: Context): String {
    return getPrimaryPublicConfigFile().absolutePath
  }

  private fun configToJson(config: SleepyConfig): String {
    return JSONObject()
            .apply {
              put(KEY_SERVER_URL, config.serverUrl)
              put(KEY_SECRET, config.secret)
              put(KEY_DEVICE_ID, config.deviceId)
              put(KEY_SHOW_NAME, config.showName)
              put(KEY_ENABLED, config.enabled)
              put(KEY_MEDIA_ENABLED, config.mediaEnabled)
              put(KEY_MEDIA_DEVICE_ID, config.mediaDeviceId)
              put(KEY_MEDIA_SHOW_NAME, config.mediaShowName)
              put(KEY_MEDIA_METHOD, config.mediaMethod)
            }
            .toString()
  }

  private fun parseConfigJson(text: String): SleepyConfig? {
    return try {
      val json = JSONObject(text)
      SleepyConfig(
              serverUrl = json.optString(KEY_SERVER_URL, ""),
              secret = json.optString(KEY_SECRET, ""),
              deviceId = json.optString(KEY_DEVICE_ID, ""),
              showName = json.optString(KEY_SHOW_NAME, ""),
              enabled = json.optBoolean(KEY_ENABLED, false),
              mediaEnabled = json.optBoolean(KEY_MEDIA_ENABLED, false),
              mediaDeviceId = json.optString(KEY_MEDIA_DEVICE_ID, ""),
              mediaShowName = json.optString(KEY_MEDIA_SHOW_NAME, ""),
              mediaMethod = json.optString(KEY_MEDIA_METHOD, MediaMethod.AUTO.name)
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun saveConfigToJsonFiles(context: Context, config: SleepyConfig): Boolean {
    val json = configToJson(config)
    var any = false
    for (file in getAllJsonCandidates(context)) {
      try {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
          parent.mkdirs()
        }
        file.writeText(json)
        // Best-effort world-readable so system_server can open without app identity.
        file.setReadable(true, false)
        parent?.setReadable(true, false)
        parent?.setExecutable(true, false)
        any = true
      } catch (_: Exception) {}
    }
    return any
  }

  private fun loadConfigFromJsonFiles(context: Context?): SleepyConfig? {
    for (file in getAllJsonCandidates(context)) {
      try {
        if (!file.exists() || !file.canRead()) continue
        val config = parseConfigJson(file.readText()) ?: continue
        if (config.hasRequiredFields()) {
          return config
        }
      } catch (_: Exception) {}
    }
    return null
  }

  private fun getPrimaryPublicConfigFile(): File {
    return File(
            "/storage/emulated/0/Android/media/$MODULE_PACKAGE_NAME/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
    )
  }

  /**
   * Two write/read targets only: the package-specific public media dir (no permission needed,
   * readable by system_server), and the app's own external-files dir as a backup for ROMs where
   * the first path behaves unexpectedly. Earlier revisions probed ~8 candidate paths per call
   * (each a filesystem stat); that cost was paid on every config read/write for no measurable
   * reliability gain, so it has been trimmed down to these two.
   */
  private fun getAllJsonCandidates(context: Context?): List<File> {
    val files = linkedSetOf<File>()
    files.add(getPrimaryPublicConfigFile())

    if (context != null) {
      try {
        context.getExternalFilesDir(null)?.let { ext ->
          files.add(File(ext, "$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
        }
      } catch (_: Exception) {}
    } else {
      val externalRoot = Environment.getExternalStorageDirectory()
      files.add(
              File(
                      externalRoot,
                      "Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
              )
      )
    }

    return files.toList()
  }

  private fun getDeviceProtectedContext(context: Context): Context {
    return context.createDeviceProtectedStorageContext()
  }

  private fun makePrefsWorldReadable(context: Context) {
    try {
      val sharedPrefsDir = File(context.dataDir, "shared_prefs")
      if (!sharedPrefsDir.exists()) return

      sharedPrefsDir.setReadable(true, false)
      sharedPrefsDir.setExecutable(true, false)

      val prefFile = File(sharedPrefsDir, "$PREF_FILE_NAME.xml")
      if (prefFile.exists()) {
        prefFile.setReadable(true, false)
      }
    } catch (_: Exception) {
      // Best-effort
    }
  }
}
