package com.rhencloud.sleepyxposed

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var serverUrlEdit: EditText
    private lateinit var secretEdit: EditText
    private lateinit var deviceIdEdit: EditText
    private lateinit var showNameEdit: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView

    // Media status reporting views
    private lateinit var mediaEnabledSwitch: Switch
    private lateinit var mediaDeviceIdEdit: EditText
    private lateinit var mediaShowNameEdit: EditText
    private lateinit var mediaMethodGroup: RadioGroup
    private lateinit var mediaRecommendationText: TextView
    private lateinit var mediaNotificationPermissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrlEdit = findViewById(R.id.server_url)
        secretEdit = findViewById(R.id.secret)
        deviceIdEdit = findViewById(R.id.device_id)
        showNameEdit = findViewById(R.id.show_name)
        enabledSwitch = findViewById(R.id.enabled_switch)
        saveButton = findViewById(R.id.save_button)
        statusText = findViewById(R.id.status_text)

        mediaEnabledSwitch = findViewById(R.id.media_enabled_switch)
        mediaDeviceIdEdit = findViewById(R.id.media_device_id)
        mediaShowNameEdit = findViewById(R.id.media_show_name)
        mediaMethodGroup = findViewById(R.id.media_method_group)
        mediaRecommendationText = findViewById(R.id.media_recommendation_text)
        mediaNotificationPermissionButton = findViewById(R.id.media_notification_permission_button)

        // Always wire buttons first so a load/recommendation failure cannot leave UI dead.
        saveButton.setOnClickListener { saveConfiguration() }
        mediaNotificationPermissionButton.setOnClickListener { openNotificationListenerSettings() }

        try {
            loadConfiguration()
            updateRecommendationText()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading configuration: ${e.message}", Toast.LENGTH_LONG)
                .show()
            e.printStackTrace()
        }
    }

    private fun loadConfiguration() {
        val config = ConfigManager.loadConfig(this)

        serverUrlEdit.setText(config.serverUrl)
        secretEdit.setText(config.secret)
        deviceIdEdit.setText(config.deviceId)
        showNameEdit.setText(config.showName)
        enabledSwitch.isChecked = config.enabled

        mediaEnabledSwitch.isChecked = config.mediaEnabled
        mediaDeviceIdEdit.setText(config.mediaDeviceId)
        mediaShowNameEdit.setText(config.mediaShowName)

        val radioId =
            when (MediaMethod.fromString(config.mediaMethod)) {
                MediaMethod.SYSTEM_HOOK -> R.id.media_method_system_hook
                MediaMethod.NOTIFICATION_LISTENER -> R.id.media_method_notification_listener
                MediaMethod.DUMPSYS_SHELL -> R.id.media_method_dumpsys_shell
                MediaMethod.AUTO -> R.id.media_method_auto
            }
        mediaMethodGroup.check(radioId)
    }

    private fun saveConfiguration() {
        val url = serverUrlEdit.text.toString()
        val secret = secretEdit.text.toString()
        val id = deviceIdEdit.text.toString()
        val showName = showNameEdit.text.toString()
        val enabled = enabledSwitch.isChecked

        if (url.isEmpty() || secret.isEmpty() || id.isEmpty() || showName.isEmpty()) {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val mediaEnabled = mediaEnabledSwitch.isChecked
        val mediaDeviceId = mediaDeviceIdEdit.text.toString()
        val mediaShowName = mediaShowNameEdit.text.toString()

        if (mediaEnabled && (mediaDeviceId.isEmpty() || mediaShowName.isEmpty())) {
            Toast.makeText(this, getString(R.string.media_fill_required_fields), Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Create config object
        val config =
                SleepyConfig(
                        serverUrl = url,
                        secret = secret,
                        deviceId = id,
                        showName = showName,
                        enabled = enabled,
                        mediaEnabled = mediaEnabled,
                        mediaDeviceId = mediaDeviceId,
                        mediaShowName = mediaShowName,
                        mediaMethod = selectedMediaMethod().name
                )

        val success = ConfigManager.saveConfig(this, config)

        if (success) {
            // Include mirror path so user can verify system_server-readable config exists.
            statusText.text =
                getString(R.string.config_saved) +
                    "\n\n" +
                    ConfigManager.getConfigFilePath(this)
            statusText.visibility = View.VISIBLE

            Toast.makeText(this, getString(R.string.config_saved_toast), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to save configuration", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectedMediaMethod(): MediaMethod {
        return when (mediaMethodGroup.checkedRadioButtonId) {
            R.id.media_method_system_hook -> MediaMethod.SYSTEM_HOOK
            R.id.media_method_notification_listener -> MediaMethod.NOTIFICATION_LISTENER
            R.id.media_method_dumpsys_shell -> MediaMethod.DUMPSYS_SHELL
            else -> MediaMethod.AUTO
        }
    }

    /** Shows the detected Android version / System UI and the auto-recommended method. */
    private fun updateRecommendationText() {
        val recommendation = RomDetector.recommend(this)
        val methodLabel =
            when (recommendation.method) {
                MediaMethod.SYSTEM_HOOK -> getString(R.string.media_method_system_hook)
                MediaMethod.NOTIFICATION_LISTENER ->
                    getString(R.string.media_method_notification_listener)
                else -> getString(R.string.media_method_system_hook)
            }

        mediaRecommendationText.text =
            getString(
                R.string.media_recommendation_format,
                recommendation.androidVersion,
                recommendation.rom.displayName,
                methodLabel,
                recommendation.reason
            )
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
