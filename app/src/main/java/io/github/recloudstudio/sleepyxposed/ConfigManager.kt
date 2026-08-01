package io.github.recloudstudio.sleepyxposed

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

  /** Complete enough for the public JSON mirror, which deliberately excludes [secret]. */
  fun hasRequiredPublicFields(): Boolean {
    return serverUrl.isNotBlank() && deviceId.isNotBlank() && showName.isNotBlank()
  }
}

/** Configuration manager for loading and saving config across app + system_server. */
object ConfigManager {
  private const val PREF_FILE_NAME = "sleepy_config"
  private const val MODULE_PACKAGE_NAME = "io.github.recloudstudio.sleepyxposed"
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
    val provider = systemContext?.let { loadViaContentProvider(it) }
    if (provider != null && provider.hasRequiredFields()) return provider

    val json = loadConfigFromJsonFiles(null)
    if (json != null && json.hasRequiredPublicFields()) return json

    loadViaLegacyXSharedPreferences()?.takeIf { it.hasRequiredFields() }?.let { return it }
    loadConfigFromPrefsXmlFiles()?.let { return it }

    return provider ?: json ?: SleepyConfig()
  }

  /** Human-readable diagnostics for why system_server cannot see config. Diagnostic-only. */
  fun describeLoadSources(systemContext: Context?): String {
    val provider = runCatching { loadViaContentProvider(systemContext) }.getOrNull()
    val json = runCatching { loadConfigFromJsonFiles(null) }.getOrNull()
    val xsp = runCatching { loadViaLegacyXSharedPreferences() }.getOrNull()
    val xml = runCatching { loadConfigFromPrefsXmlFiles() }.getOrNull()
    val existing =
            getAllJsonCandidates(null).filter { it.exists() }.joinToString(",") { it.absolutePath }
    return "provider=${provider?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}; " +
            "json=${json?.let { if (it.hasRequiredPublicFields()) "ok" else "incomplete" } ?: "fail"}; " +
            "xsp=${xsp?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}; " +
            "prefsXml=${xml?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}; " +
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
                        serverUrl = str(ConfigContentProvider.COLUMN_SERVER_URL),
                        secret = str(ConfigContentProvider.COLUMN_SECRET),
                        deviceId = str(ConfigContentProvider.COLUMN_DEVICE_ID),
                        showName = str(ConfigContentProvider.COLUMN_SHOW_NAME),
                        enabled = bool(ConfigContentProvider.COLUMN_ENABLED),
                        mediaEnabled = bool(ConfigContentProvider.COLUMN_MEDIA_ENABLED),
                        mediaDeviceId = str(ConfigContentProvider.COLUMN_MEDIA_DEVICE_ID),
                        mediaShowName = str(ConfigContentProvider.COLUMN_MEDIA_SHOW_NAME),
                        mediaMethod =
                                str(ConfigContentProvider.COLUMN_MEDIA_METHOD)
                                        .ifBlank { MediaMethod.AUTO.name }
                )
              }
    } catch (_: Exception) {
      null
    }
  }

  /** Legacy [de.robv.android.xposed.XSharedPreferences] read for older ROMs where the provider cannot be reached. */
  private fun loadViaLegacyXSharedPreferences(): SleepyConfig? {
    return try {
      val clazz = Class.forName("de.robv.android.xposed.XSharedPreferences")
      val constructor = clazz.getConstructor(String::class.java, String::class.java)
      val pref = constructor.newInstance(MODULE_PACKAGE_NAME, PREF_FILE_NAME)

      clazz.getMethod("reload").invoke(pref)

      val getString = clazz.getMethod("getString", String::class.java, String::class.java)
      val getBoolean =
              clazz.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)

      SleepyConfig(
              serverUrl = (getString.invoke(pref, KEY_SERVER_URL, "") as? String) ?: "",
              secret = (getString.invoke(pref, KEY_SECRET, "") as? String) ?: "",
              deviceId = (getString.invoke(pref, KEY_DEVICE_ID, "") as? String) ?: "",
              showName = (getString.invoke(pref, KEY_SHOW_NAME, "") as? String) ?: "",
              enabled = (getBoolean.invoke(pref, KEY_ENABLED, false) as? Boolean) ?: false,
              mediaEnabled =
                      (getBoolean.invoke(pref, KEY_MEDIA_ENABLED, false) as? Boolean) ?: false,
              mediaDeviceId = (getString.invoke(pref, KEY_MEDIA_DEVICE_ID, "") as? String) ?: "",
              mediaShowName = (getString.invoke(pref, KEY_MEDIA_SHOW_NAME, "") as? String) ?: "",
              mediaMethod =
                      (getString.invoke(pref, KEY_MEDIA_METHOD, MediaMethod.AUTO.name) as? String)
                              ?: MediaMethod.AUTO.name
      )
    } catch (_: Exception) {
      null
    }
  }

  /** Parse the app's SharedPreferences XML directly when other paths are SELinux-blocked. */
  private fun loadConfigFromPrefsXmlFiles(): SleepyConfig? {
    for (file in getModulePrefsXmlCandidates()) {
      try {
        if (!file.exists() || !file.canRead()) continue
        parseSharedPreferencesXml(file.readText())?.let { config ->
          if (config.hasRequiredFields() || config.mediaEnabled) {
            return config
          }
        }
      } catch (_: Exception) {}
    }
    return null
  }

  private fun getModulePrefsXmlCandidates(): List<File> {
    val fileName = "$PREF_FILE_NAME.xml"
    return listOf(
            File("/data/user_de/0/$MODULE_PACKAGE_NAME/shared_prefs/$fileName"),
            File("/data/user/0/$MODULE_PACKAGE_NAME/shared_prefs/$fileName"),
            File("/data/data/$MODULE_PACKAGE_NAME/shared_prefs/$fileName")
    )
  }

  private fun parseSharedPreferencesXml(xml: String): SleepyConfig? {
    if (!xml.contains("<map")) return null

    fun stringValue(key: String): String {
      val re =
              Regex(
                      """<string\s+name="$key">(.*?)</string>""",
                      setOf(RegexOption.DOT_MATCHES_ALL)
              )
      val raw = re.find(xml)?.groupValues?.getOrNull(1) ?: return ""
      return raw
              .replace("&lt;", "<")
              .replace("&gt;", ">")
              .replace("&amp;", "&")
              .replace("&quot;", "\"")
              .replace("&apos;", "'")
    }

    fun booleanValue(key: String, default: Boolean = false): Boolean {
      val re = Regex("""<boolean\s+name="$key"\s+value="(true|false)"\s*/>""")
      return re.find(xml)?.groupValues?.getOrNull(1)?.toBoolean() ?: default
    }

    return SleepyConfig(
            serverUrl = stringValue(KEY_SERVER_URL),
            secret = stringValue(KEY_SECRET),
            deviceId = stringValue(KEY_DEVICE_ID),
            showName = stringValue(KEY_SHOW_NAME),
            enabled = booleanValue(KEY_ENABLED, false),
            mediaEnabled = booleanValue(KEY_MEDIA_ENABLED, false),
            mediaDeviceId = stringValue(KEY_MEDIA_DEVICE_ID),
            mediaShowName = stringValue(KEY_MEDIA_SHOW_NAME),
            mediaMethod = stringValue(KEY_MEDIA_METHOD).ifBlank { MediaMethod.AUTO.name }
    )
  }

  /** Save configuration in module app process */
  fun saveConfig(context: Context, config: SleepyConfig): Boolean {
    val deSaved = writeConfigToPrefs(getDeviceProtectedContext(context), config)
    val ceSaved = writeConfigToPrefs(context, config)
    // JSON mirror for system_server (private app data is SELinux-blocked from system).
    val jsonSaved = saveConfigToJsonFiles(context, config)

    // cachedSystemConfig is a process-local static: clearing it here only invalidates the
    // copy in THIS process (the app). system_server's own copy refreshes on its next poll once
    // the TTL window elapses; the notifyChange below lets a system-side ContentObserver hook
    // into that (there is currently none registered), so the TTL is the effective bound there.
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

  /** [secret] is written only when [includeSecret] is true (private prefs); never to the public JSON mirror. */
  private fun configToJson(config: SleepyConfig, includeSecret: Boolean = true): String {
    return JSONObject()
            .apply {
              put(KEY_SERVER_URL, config.serverUrl)
              if (includeSecret) put(KEY_SECRET, config.secret)
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
    // The public mirror never carries the secret — it authenticates device reports, so it must
    // not be readable from a world-readable file. system_server gets it via the provider query.
    val json = configToJson(config, includeSecret = false)
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
        // The public mirror has no secret; only require the fields it actually carries.
        if (config.hasRequiredPublicFields()) {
          return config
        }
      } catch (_: Exception) {}
    }
    return null
  }

  private fun getPrimaryPublicConfigFile(): File {
    return File(
            Environment.getExternalStorageDirectory(),
            "Android/media/$MODULE_PACKAGE_NAME/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
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
