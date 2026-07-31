package com.rhencloud.sleepyxposed

import android.content.Context
import android.os.Environment
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
)

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

  /** Load configuration for module app process */
  fun loadConfig(context: Context): SleepyConfig {
    return try {
      val de = readConfigFromPrefs(getDeviceProtectedContext(context), requireComplete = false)
      if (de != null && de.hasRequiredFields()) return de

      val ce = readConfigFromPrefs(context, requireComplete = false)
      if (ce != null && ce.hasRequiredFields()) return ce

      loadConfigFromJsonFiles(context)
              ?: de
              ?: ce
              ?: SleepyConfig()
    } catch (_: Exception) {
      loadConfigFromJsonFiles(context) ?: SleepyConfig()
    }
  }

  /**
   * Load configuration inside hooked processes (typically system_server).
   *
   * Private app data is SELinux-blocked from system_server on modern ROMs, and classic
   * XSharedPreferences is unreliable under libxposed API 101. Prefer:
   * 1. ContentProvider (app process reads its own prefs)
   * 2. Public / media JSON files written on save
   * 3. Legacy XSharedPreferences / prefs XML (best-effort)
   */
  fun loadConfigFromXSharedPreferences(systemContext: Context? = null): SleepyConfig {
    systemContext?.let { ctx ->
      loadViaContentProvider(ctx)?.takeIf { it.hasRequiredFields() }?.let {
        return it
      }
    }

    loadConfigFromJsonFiles(null)?.takeIf { it.hasRequiredFields() }?.let {
      return it
    }

    loadViaLegacyXSharedPreferences()?.takeIf { it.hasRequiredFields() }?.let {
      return it
    }

    loadConfigFromPrefsXmlFiles()?.takeIf { it.hasRequiredFields() }?.let {
      return it
    }

    return loadViaContentProvider(systemContext)
            ?: loadConfigFromJsonFiles(null)
            ?: loadViaLegacyXSharedPreferences()
            ?: loadConfigFromPrefsXmlFiles()
            ?: SleepyConfig()
  }

  /** Human-readable diagnostics for why system_server cannot see config. */
  fun describeLoadSources(systemContext: Context?): String {
    val parts = mutableListOf<String>()
    val provider = runCatching { loadViaContentProvider(systemContext) }.getOrNull()
    parts.add(
            "provider=${provider?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}"
    )
    val json = runCatching { loadConfigFromJsonFiles(null) }.getOrNull()
    parts.add("json=${json?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}")
    val xsp = runCatching { loadViaLegacyXSharedPreferences() }.getOrNull()
    parts.add("xsp=${xsp?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}")
    val xml = runCatching { loadConfigFromPrefsXmlFiles() }.getOrNull()
    parts.add("prefsXml=${xml?.let { if (it.hasRequiredFields()) "ok" else "incomplete" } ?: "fail"}")
    val existing =
            getAllJsonCandidates(null).filter { it.exists() }.joinToString(",") { it.absolutePath }
    parts.add("jsonFiles=[${existing.ifBlank { "none" }}]")
    return parts.joinToString("; ")
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
    // JSON mirrors for system_server (private app data is SELinux-blocked from system).
    val jsonSaved = saveConfigToJsonFiles(context, config)

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

  private fun getAllJsonCandidates(context: Context?): List<File> {
    val files = linkedSetOf<File>()

    // Prefer Android/media — app can write without special storage permission; system can usually read.
    files.add(getPrimaryPublicConfigFile())
    files.add(
            File("/sdcard/Android/media/$MODULE_PACKAGE_NAME/$FALLBACK_DIR/$FALLBACK_FILE_NAME")
    )

    if (context != null) {
      try {
        context.externalMediaDirs?.forEach { mediaDir ->
          if (mediaDir != null) {
            files.add(File(mediaDir, "$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
          }
        }
      } catch (_: Exception) {}
      try {
        context.getExternalFilesDir(null)?.let { ext ->
          files.add(File(ext, "$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
        }
      } catch (_: Exception) {}
    }

    val externalRoot = Environment.getExternalStorageDirectory()
    files.add(File(externalRoot, "Android/media/$MODULE_PACKAGE_NAME/$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
    files.add(File(externalRoot, "Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
    files.add(File(externalRoot, "$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
    files.add(File("/sdcard/$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
    files.add(File("/storage/emulated/0/$FALLBACK_DIR/$FALLBACK_FILE_NAME"))
    files.add(
            File(
                    externalRoot,
                    "Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
            )
    )

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

  private fun SleepyConfig.hasRequiredFields(): Boolean {
    return serverUrl.isNotBlank() &&
            secret.isNotBlank() &&
            deviceId.isNotBlank() &&
            showName.isNotBlank()
  }
}
