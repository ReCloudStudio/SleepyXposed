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

/** Configuration manager for loading and saving config.json */
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
    val prefContext = getPrefContext(context)
    @Suppress("DEPRECATION")
    val pref = prefContext.getSharedPreferences(PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
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
                    mediaMethod = pref.getString(KEY_MEDIA_METHOD, MediaMethod.AUTO.name)
                                    ?: MediaMethod.AUTO.name
            )

    return if (config.hasRequiredFields()) config else loadConfigFromFallbackFile() ?: config
  }

  /** Load configuration for hooked process via XSharedPreferences */
  fun loadConfigFromXSharedPreferences(): SleepyConfig {
    return try {
      val clazz = Class.forName("de.robv.android.xposed.XSharedPreferences")
      val constructor = clazz.getConstructor(String::class.java, String::class.java)
      val pref = constructor.newInstance(MODULE_PACKAGE_NAME, PREF_FILE_NAME)

      clazz.getMethod("reload").invoke(pref)

      val getString = clazz.getMethod("getString", String::class.java, String::class.java)
      val getBoolean =
              clazz.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)

      val config =
              SleepyConfig(
                      serverUrl = (getString.invoke(pref, KEY_SERVER_URL, "") as? String) ?: "",
                      secret = (getString.invoke(pref, KEY_SECRET, "") as? String) ?: "",
                      deviceId = (getString.invoke(pref, KEY_DEVICE_ID, "") as? String) ?: "",
                      showName = (getString.invoke(pref, KEY_SHOW_NAME, "") as? String) ?: "",
                      enabled = (getBoolean.invoke(pref, KEY_ENABLED, false) as? Boolean) ?: false,
                      mediaEnabled =
                              (getBoolean.invoke(pref, KEY_MEDIA_ENABLED, false) as? Boolean)
                                      ?: false,
                      mediaDeviceId =
                              (getString.invoke(pref, KEY_MEDIA_DEVICE_ID, "") as? String) ?: "",
                      mediaShowName =
                              (getString.invoke(pref, KEY_MEDIA_SHOW_NAME, "") as? String) ?: "",
                      mediaMethod =
                              (getString.invoke(pref, KEY_MEDIA_METHOD, MediaMethod.AUTO.name)
                                      as? String)
                                      ?: MediaMethod.AUTO.name
              )

      if (config.hasRequiredFields()) {
        config
      } else {
        loadConfigFromFallbackFile() ?: config
      }
    } catch (e: Exception) {
      loadConfigFromFallbackFile() ?: SleepyConfig()
    }
  }

  /** Save configuration in module app process */
  fun saveConfig(context: Context, config: SleepyConfig): Boolean {
    return try {
      val prefContext = getPrefContext(context)
      @Suppress("DEPRECATION")
      val pref = prefContext.getSharedPreferences(PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
      val prefSaved =
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

      makePrefsWorldReadable(prefContext)

      val fallbackSaved = saveConfigToFallbackFile(context, config)
      prefSaved || fallbackSaved
    } catch (e: Exception) {
      saveConfigToFallbackFile(context, config)
    }
  }

  /** Get preference XML path for debugging */
  fun getConfigFilePath(context: Context): String {
    return getAppFallbackConfigFile(context).absolutePath
  }

  private fun saveConfigToFallbackFile(context: Context, config: SleepyConfig): Boolean {
    return try {
      val file = getAppFallbackConfigFile(context)
      val parent = file.parentFile
      if (parent != null && !parent.exists()) {
        parent.mkdirs()
      }

      val json =
              JSONObject().apply {
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

      file.writeText(json.toString())
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun loadConfigFromFallbackFile(): SleepyConfig? {
    for (file in getHookFallbackConfigCandidates()) {
      try {
        if (!file.exists()) {
          continue
        }

        val json = JSONObject(file.readText())
        val config =
                SleepyConfig(
                        serverUrl = json.optString(KEY_SERVER_URL, ""),
                        secret = json.optString(KEY_SECRET, ""),
                        deviceId = json.optString(KEY_DEVICE_ID, ""),
                        showName = json.optString(KEY_SHOW_NAME, ""),
                        enabled = json.optBoolean(KEY_ENABLED, false),
                        mediaEnabled = json.optBoolean(KEY_MEDIA_ENABLED, false),
                        mediaDeviceId = json.optString(KEY_MEDIA_DEVICE_ID, ""),
                        mediaShowName = json.optString(KEY_MEDIA_SHOW_NAME, ""),
                        mediaMethod =
                                json.optString(KEY_MEDIA_METHOD, MediaMethod.AUTO.name)
                )
        if (config.hasRequiredFields()) {
          return config
        }
      } catch (_: Exception) {}
    }

    return null
  }

  private fun getAppFallbackConfigFile(context: Context): File {
    val appExternalDir = context.getExternalFilesDir(null)
    val baseDir =
            if (appExternalDir != null) {
              File(appExternalDir, FALLBACK_DIR)
            } else {
              File(
                      Environment.getExternalStorageDirectory(),
                      "Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR"
              )
            }
    return File(baseDir, FALLBACK_FILE_NAME)
  }

  private fun getHookFallbackConfigCandidates(): List<File> {
    val candidates = mutableListOf<File>()
    val externalRoot = Environment.getExternalStorageDirectory()

    candidates.add(
            File(
                    externalRoot,
                    "Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
            )
    )
    candidates.add(
            File(
                    externalRoot,
                    "Android/media/$MODULE_PACKAGE_NAME/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
            )
    )
    candidates.add(
            File(
                    "/sdcard/Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
            )
    )
    candidates.add(
            File(
                    "/storage/emulated/0/Android/data/$MODULE_PACKAGE_NAME/files/$FALLBACK_DIR/$FALLBACK_FILE_NAME"
            )
    )

    return candidates
  }

  private fun getPrefContext(context: Context): Context {
    val deviceProtected = context.createDeviceProtectedStorageContext()
    // Move existing prefs so the system_server can read them before unlock
    deviceProtected.moveSharedPreferencesFrom(context, PREF_FILE_NAME)
    return deviceProtected
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
      // Best-effort; fallback file will still be used if needed
    }
  }

  private fun SleepyConfig.hasRequiredFields(): Boolean {
    return serverUrl.isNotBlank() &&
            secret.isNotBlank() &&
            deviceId.isNotBlank() &&
            showName.isNotBlank()
  }
}
