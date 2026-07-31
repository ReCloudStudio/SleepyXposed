package io.github.recloudstudio.sleepyxposed

import java.util.concurrent.TimeUnit
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Client for interacting with the Sleepy API Implements /api/device/set endpoint */
object SleepyApiClient {
    private const val JSON_MIME = "application/json"
    private const val USER_AGENT = "SleepyXposed"
    private const val API_ENDPOINT = "/api/device/set"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }

    /**
     * Send device status to Sleepy server
     * @param baseUrl Server base URL
     * @param secret Server secret for authentication
     * @param id Device ID
     * @param showName Display name for the device
     * @param using Whether the device is being used
     * @param status Status text (e.g., app name with battery info)
     * @param callback Callback for handling response
     */
    fun sendDeviceStatus(
            baseUrl: String,
            secret: String,
            id: String,
            showName: String,
            using: Boolean,
            status: String,
            callback: Callback
    ) {
        // Build complete URL
        val fullUrl = buildApiUrl(baseUrl)

        val requestBody = createRequestBody(secret, id, showName, using, status)
        val request =
                Request.Builder()
                        .url(fullUrl)
                        .post(requestBody)
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

        httpClient.newCall(request).enqueue(callback)
    }

    /** Build full API URL from base URL Handles trailing slashes properly */
    private fun buildApiUrl(baseUrl: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        return "$normalizedBase$API_ENDPOINT"
    }

    private fun createRequestBody(
            secret: String,
            id: String,
            showName: String,
            using: Boolean,
            status: String
    ): RequestBody {
        val json =
                JSONObject().apply {
                    put("secret", secret)
                    put("id", id)
                    put("show_name", showName)
                    put("using", using)
                    put("status", status)
                }
        return json.toString().toRequestBody(JSON_MIME.toMediaType())
    }
}
