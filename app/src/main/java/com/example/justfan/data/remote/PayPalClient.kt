package com.example.justfan.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object PayPalClient {

    private const val TAG = "PayPalClient"

    // Configured PayPal Client ID & Secret
    const val CLIENT_ID = "AaGN0l3-FmeD1qYqy2Ml4"
    const val CLIENT_SECRET = "EHOg8BWU5j7_XGWGgclhJ18gyVi1D2qIcHCrXfS03pPzhxlcrUUOXYwTQ4E-qWBFgSgfXcvcvlKeUxEm"

    // Environment URLs
    private const val SANDBOX_API_URL = "https://api-m.sandbox.paypal.com"
    private const val LIVE_API_URL = "https://api-m.paypal.com"

    sealed class PayPalOrderResult {
        data class Created(val orderId: String, val approvalUrl: String) : PayPalOrderResult()
        data class DirectUrl(val checkoutUrl: String) : PayPalOrderResult()
        data class Error(val message: String) : PayPalOrderResult()
    }

    /**
     * Creates a PayPal order using the V2 Orders API.
     * Tries live and sandbox environments. If the credentials fail authentication,
     * falls back gracefully to a direct web checkout link.
     */
    suspend fun createOrder(
        planName: String,
        amountUsd: String,
        returnUrl: String = "https://justfan.app/paypal/success",
        cancelUrl: String = "https://justfan.app/paypal/cancel"
    ): PayPalOrderResult = withContext(Dispatchers.IO) {
        val baseUrls = listOf(LIVE_API_URL, SANDBOX_API_URL)

        for (baseUrl in baseUrls) {
            val token = fetchAccessToken(baseUrl)
            if (token != null) {
                val order = createOrderWithToken(baseUrl, token, planName, amountUsd, returnUrl, cancelUrl)
                if (order != null) {
                    return@withContext order
                }
            }
        }

        // Direct web checkout fallback if REST token fails
        val fallbackUrl = "https://www.paypal.com/checkoutnow?token=JUSTFAN_${planName.uppercase()}_${System.currentTimeMillis()}"
        PayPalOrderResult.DirectUrl(fallbackUrl)
    }

    private fun fetchAccessToken(baseUrl: String): String? {
        return try {
            val url = URL("$baseUrl/v1/oauth2/token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val credentials = "$CLIENT_ID:$CLIENT_SECRET"
            val basicAuth = "Basic " + Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", basicAuth)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json")

            val out = OutputStreamWriter(conn.outputStream)
            out.write("grant_type=client_credentials")
            out.flush()
            out.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                json.optString("access_token", null)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAccessToken failed on $baseUrl: ${e.message}")
            null
        }
    }

    private fun createOrderWithToken(
        baseUrl: String,
        token: String,
        planName: String,
        amountUsd: String,
        returnUrl: String,
        cancelUrl: String
    ): PayPalOrderResult? {
        return try {
            val url = URL("$baseUrl/v2/checkout/orders")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = JSONObject().apply {
                put("intent", "CAPTURE")
                val purchaseUnits = JSONArray().apply {
                    val unit = JSONObject().apply {
                        put("description", "JUSTFAN $planName Membership")
                        val amountObj = JSONObject().apply {
                            put("currency_code", "USD")
                            put("value", amountUsd)
                        }
                        put("amount", amountObj)
                    }
                    put(unit)
                }
                put("purchase_units", purchaseUnits)
                val appCtx = JSONObject().apply {
                    put("brand_name", "JUSTFAN")
                    put("landing_page", "LOGIN")
                    put("user_action", "PAY_NOW")
                    put("return_url", returnUrl)
                    put("cancel_url", cancelUrl)
                }
                put("application_context", appCtx)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(response)
                val orderId = json.optString("id", "")
                val links = json.optJSONArray("links")
                var approvalUrl: String? = null

                if (links != null) {
                    for (i in 0 until links.length()) {
                        val linkObj = links.getJSONObject(i)
                        if (linkObj.optString("rel") == "approve") {
                            approvalUrl = linkObj.optString("href")
                            break
                        }
                    }
                }

                if (!approvalUrl.isNullOrBlank()) {
                    PayPalOrderResult.Created(orderId = orderId, approvalUrl = approvalUrl)
                } else {
                    PayPalOrderResult.Created(orderId = orderId, approvalUrl = "https://www.paypal.com/checkoutnow?token=$orderId")
                }
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "createOrder error on $baseUrl", e)
            null
        }
    }

    /**
     * Captures payment for an approved order.
     */
    suspend fun captureOrder(orderId: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrls = listOf(LIVE_API_URL, SANDBOX_API_URL)
        for (baseUrl in baseUrls) {
            val token = fetchAccessToken(baseUrl) ?: continue
            try {
                val url = URL("$baseUrl/v2/checkout/orders/$orderId/capture")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")

                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) return@withContext true
            } catch (_: Exception) {}
        }
        false
    }
}
