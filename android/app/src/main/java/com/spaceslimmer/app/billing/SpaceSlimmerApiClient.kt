package com.spaceslimmer.app.billing

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.spaceslimmer.app.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Production-ready Android API Client for SpaceSlimmer Lightweight Backend.
 * Uses native HttpURLConnection and org.json — zero third-party dependencies required.
 */
object SpaceSlimmerApiClient {

    // Live AWS API Gateway endpoint (can be switched to http://10.0.2.2:3000 for local offline testing)
    private var baseUrl: String = "https://6ta8jul1hf.execute-api.us-east-2.amazonaws.com/dev"

    private const val PREFS_NAME = "spaceslimmer_billing"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_PRO = "pro_unlocked"
    private const val KEY_PLAN_TYPE = "plan_type"
    private const val KEY_REMAINING_QUOTA = "remaining_quota"
    private const val KEY_TOTAL_QUOTA = "total_quota"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_DEVICE_ID = "device_unique_id"

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getBaseUrl(): String = baseUrl

    fun isLoggedIn(context: Context): Boolean {
        return !getAuthToken(context).isNullOrBlank()
    }

    fun getAuthToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    fun getUserEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
    }

    fun getPlanType(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAN_TYPE, "free") ?: "free"
    }

    fun getExpiresAt(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EXPIRES_AT, null)
    }

    fun getRemainingQuota(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_REMAINING_QUOTA, 30)
    }

    fun getTotalQuota(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TOTAL_QUOTA, 30)
    }

    fun getOrCreateDeviceId(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = sp.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = "${Build.BRAND}_${Build.MODEL}_${UUID.randomUUID().toString().take(8)}"
            sp.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun clearAuth(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .remove(KEY_EXPIRES_AT)
            .putBoolean(KEY_PRO, false)
            .putString(KEY_PLAN_TYPE, "free")
            .putInt(KEY_REMAINING_QUOTA, 30)
            .putInt(KEY_TOTAL_QUOTA, 30)
            .apply()
    }

    data class ApiResult<T>(
        val isSuccess: Boolean,
        val data: T? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    data class SubscriptionInfo(
        val isPro: Boolean,
        val planType: String,
        val status: String,
        val expiresAt: String?,
        val period: String,
        val totalQuota: Int,
        val usedQuota: Int,
        val remainingQuota: Int,
        val dailyEmergencyAvailable: Boolean,
        val maxResolution: String,
        val allowHevc: Boolean,
        val maxBatchVideo: Int
    )

    /**
     * Register a new user.
     */
    suspend fun register(context: Context, email: String, pass: String): ApiResult<SubscriptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                }
                val res = executeRequest(context, "/api/auth/register", "POST", body)
                if (res.optInt("statusCode") in 200..299) {
                    val token = res.optString("token")
                    val subObj = res.optJSONObject("subscription")
                    saveAuth(context, token, email, subObj)
                    val info = parseSubscription(subObj)
                    ApiResult(true, data = info)
                } else {
                    ApiResult(false, errorCode = res.optString("error"), errorMessage = res.optString("message", context.getString(R.string.api_err_register_failed)))
                }
            } catch (e: Exception) {
                ApiResult(false, errorMessage = e.localizedMessage ?: context.getString(R.string.api_err_network_failed))
            }
        }
    }

    /**
     * User Login.
     */
    suspend fun login(context: Context, email: String, pass: String): ApiResult<SubscriptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                }
                val res = executeRequest(context, "/api/auth/login", "POST", body)
                if (res.optInt("statusCode") in 200..299) {
                    val token = res.optString("token")
                    val subObj = res.optJSONObject("subscription")
                    saveAuth(context, token, email, subObj)
                    val info = parseSubscription(subObj)
                    ApiResult(true, data = info)
                } else {
                    ApiResult(false, errorCode = res.optString("error"), errorMessage = res.optString("message", context.getString(R.string.api_err_login_failed)))
                }
            } catch (e: Exception) {
                ApiResult(false, errorMessage = e.localizedMessage ?: context.getString(R.string.api_err_network_failed))
            }
        }
    }

    /**
     * Fetch latest subscription status & limits from server.
     */
    suspend fun getStatus(context: Context): ApiResult<SubscriptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val res = executeRequest(context, "/api/subscription/status", "GET", null)
                if (res.optInt("statusCode") in 200..299) {
                    val email = getUserEmail(context) ?: ""
                    val token = getAuthToken(context) ?: ""
                    saveAuth(context, token, email, res)
                    ApiResult(true, data = parseSubscription(res))
                } else {
                    ApiResult(false, errorCode = res.optString("error"), errorMessage = res.optString("message"))
                }
            } catch (e: Exception) {
                ApiResult(false, errorMessage = e.localizedMessage ?: context.getString(R.string.api_err_quota_fetch_failed))
            }
        }
    }

    /**
     * Consume compression quota.
     */
    suspend fun consumeQuota(context: Context, count: Int = 1, mediaType: String = "video"): ApiResult<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("count", count)
                    put("mediaType", mediaType)
                }
                val res = executeRequest(context, "/api/subscription/consume", "POST", body)
                if (res.optInt("statusCode") in 200..299) {
                    val isPro = res.optBoolean("isPro", false)
                    val quota = res.optJSONObject("quota")
                    val remaining = quota?.optInt("remaining") ?: (if (isPro) 999999 else 0)
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_PRO, isPro)
                        .putInt(KEY_REMAINING_QUOTA, remaining)
                        .apply()
                    ApiResult(true, data = remaining)
                } else {
                    ApiResult(
                        false,
                        errorCode = res.optString("error"),
                        errorMessage = res.optString("message", context.getString(R.string.api_err_quota_exhausted))
                    )
                }
            } catch (e: Exception) {
                ApiResult(false, errorMessage = e.localizedMessage ?: context.getString(R.string.api_err_quota_consume_failed))
            }
        }
    }

    /**
     * Activate a PRO license key.
     */
    suspend fun activateLicense(context: Context, licenseKey: String): ApiResult<SubscriptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("licenseKey", licenseKey.trim().uppercase())
                }
                val res = executeRequest(context, "/api/subscription/activate", "POST", body)
                if (res.optInt("statusCode") in 200..299) {
                    val subObj = res.optJSONObject("subscription")
                    val isPro = subObj?.optBoolean("isPro", true) ?: true
                    val plan = subObj?.optString("planType", "pro-yearly") ?: "pro-yearly"
                    val expiresAt = if (subObj?.has("expiresAt") == true && !subObj.isNull("expiresAt")) subObj.optString("expiresAt") else null
                    val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_PRO, isPro)
                        .putString(KEY_PLAN_TYPE, plan)
                        .putInt(KEY_REMAINING_QUOTA, 999999)
                    if (expiresAt != null) {
                        editor.putString(KEY_EXPIRES_AT, expiresAt)
                    }
                    editor.apply()
                    ApiResult(true, data = parseSubscription(subObj))
                } else {
                    ApiResult(false, errorCode = res.optString("error"), errorMessage = res.optString("message", context.getString(R.string.api_err_license_invalid)))
                }
            } catch (e: Exception) {
                ApiResult(false, errorMessage = e.localizedMessage ?: context.getString(R.string.api_err_license_activate_failed))
            }
        }
    }

    /**
     * Logout and revoke session on the server.
     */
    suspend fun logout(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                executeRequest(context, "/api/auth/logout", "POST", null)
                clearAuth(context)
                true
            } catch (e: Exception) {
                clearAuth(context)
                true
            }
        }
    }

    private fun saveAuth(context: Context, token: String, email: String, subObj: JSONObject?) {
        val isPro = subObj?.optBoolean("isPro", false) ?: false
        val plan = subObj?.optString("planType", "free") ?: "free"
        val remaining = subObj?.optJSONObject("quota")?.optInt("remaining") ?: 30
        val total = subObj?.optJSONObject("quota")?.optInt("total") ?: 30
        val expiresAt = if (subObj?.has("expiresAt") == true && !subObj.isNull("expiresAt")) subObj.optString("expiresAt") else null

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_PRO, isPro)
            .putString(KEY_PLAN_TYPE, plan)
            .putInt(KEY_REMAINING_QUOTA, remaining)
            .putInt(KEY_TOTAL_QUOTA, total)
        if (expiresAt != null) {
            editor.putString(KEY_EXPIRES_AT, expiresAt)
        } else {
            editor.remove(KEY_EXPIRES_AT)
        }
        editor.apply()
    }

    private fun parseSubscription(subObj: JSONObject?): SubscriptionInfo {
        if (subObj == null) {
            return SubscriptionInfo(false, "free", "active", null, "", 30, 0, 30, false, "1080p", false, 3)
        }
        val quota = subObj.optJSONObject("quota")
        val limits = subObj.optJSONObject("limits")
        val batch = limits?.optJSONObject("maxBatchFiles")

        return SubscriptionInfo(
            isPro = subObj.optBoolean("isPro", false),
            planType = subObj.optString("planType", "free"),
            status = subObj.optString("status", "active"),
            expiresAt = if (subObj.has("expiresAt") && !subObj.isNull("expiresAt")) subObj.optString("expiresAt") else null,
            period = quota?.optString("period", "") ?: "",
            totalQuota = quota?.optInt("total", 30) ?: 30,
            usedQuota = quota?.optInt("used", 0) ?: 0,
            remainingQuota = quota?.optInt("remaining", 30) ?: 30,
            dailyEmergencyAvailable = quota?.optBoolean("dailyEmergencyAvailable", false) ?: false,
            maxResolution = limits?.optString("maxResolution", "1080p") ?: "1080p",
            allowHevc = limits?.optBoolean("allowHevc", false) ?: false,
            maxBatchVideo = batch?.optInt("video", 3) ?: 3
        )
    }

    private fun executeRequest(
        context: Context,
        path: String,
        method: String,
        jsonBody: JSONObject?
    ): JSONObject {
        val url = URL("$baseUrl$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Device-Id", getOrCreateDeviceId(context))

            val token = getAuthToken(context)
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

        if (jsonBody != null && (method == "POST" || method == "PUT")) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }
        }

        val statusCode = conn.responseCode
        val inputStream = if (statusCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val responseText = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }

        return try {
            val json = JSONObject(responseText)
            json.put("statusCode", statusCode)
            json
        } catch (e: Exception) {
            JSONObject().apply {
                put("statusCode", statusCode)
                put("rawBody", responseText)
            }
        } finally {
            conn.disconnect()
        }
    }
}
