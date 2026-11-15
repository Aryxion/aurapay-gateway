package com.karuhundeveloper.notification.aurapay.network

import com.google.gson.Gson
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayRecord
import com.karuhundeveloper.notification.aurapay.model.AuraPayConfig
import com.karuhundeveloper.notification.aurapay.model.DeviceHealth
import com.karuhundeveloper.notification.aurapay.security.HmacSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class RelayResponse(
    val success: Boolean,
    val status: String? = null,
    val matchedOrderId: String? = null,
    val message: String? = null,
    val error: String? = null
)

class AuraPayRelayClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendPaymentWebhook(record: AuraPayRecord, config: AuraPayConfig): RelayResponse =
        withContext(Dispatchers.IO) {
            try {
                val payloadMap = mapOf(
                    "idempotencyKey" to record.idempotencyKey,
                    "amount" to record.amount,
                    "payerName" to record.payerName,
                    "notificationTime" to record.notificationTime,
                    "captureTimestamp" to record.timestamp,
                    "sourceApp" to record.sourceApp,
                    "rawText" to record.rawText,
                    "deviceId" to config.deviceId
                )
                val json = gson.toJson(payloadMap)
                val signature = HmacSigner.sign(json, config.webhookSecret)

                val url = "${config.serverUrl.trimEnd('/')}/api/relay/webhook"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-aurapay-signature", signature)
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        try {
                            val map = gson.fromJson(body, Map::class.java)
                            RelayResponse(
                                success = true,
                                status = map["status"]?.toString(),
                                matchedOrderId = map["matchedOrderId"]?.toString(),
                                message = map["message"]?.toString()
                            )
                        } catch (_: Exception) {
                            RelayResponse(success = true, message = body)
                        }
                    } else {
                        RelayResponse(
                            success = false,
                            error = "HTTP ${response.code}: $body"
                        )
                    }
                }
            } catch (e: Exception) {
                RelayResponse(success = false, error = e.message ?: "Network error")
            }
        }

    suspend fun sendHeartbeat(health: DeviceHealth, config: AuraPayConfig): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(
                    mapOf(
                        "deviceId" to config.deviceId,
                        "batteryLevel" to health.batteryLevel,
                        "isCharging" to health.isCharging,
                        "networkType" to health.networkType,
                        "cpuWakeLock" to health.cpuWakeLock,
                        "wifiLock" to health.wifiLock,
                        "listenerActive" to health.listenerActive,
                        "batteryOptBypassed" to health.batteryOptBypassed,
                        "appVersion" to health.appVersion
                    )
                )
                val url = "${config.serverUrl.trimEnd('/')}/api/relay/heartbeat"
                val request = Request.Builder()
                    .url(url)
                    .post(json.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }

    suspend fun testDatabase(config: AuraPayConfig): String =
        withContext(Dispatchers.IO) {
            try {
                val url = "${config.serverUrl.trimEnd('/')}/api/relay/test-db"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    response.body?.string() ?: "Empty response"
                }
            } catch (e: Exception) {
                "Error connecting to server: ${e.message}"
            }
        }

    suspend fun updateSystemConfig(
        config: AuraPayConfig,
        mongoUri: String?,
        upiVpa: String?,
        merchantName: String?
    ): String = withContext(Dispatchers.IO) {
        try {
            val payload = mutableMapOf<String, Any>()
            mongoUri?.let { payload["mongoUri"] = it }
            upiVpa?.let { payload["merchantUpiVpa"] = it }
            merchantName?.let { payload["merchantName"] = it }
            payload["webhookSecret"] = config.webhookSecret

            val json = gson.toJson(payload)
            val url = "${config.serverUrl.trimEnd('/')}/api/relay/system-config"
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.body?.string() ?: "Empty response"
            }
        } catch (e: Exception) {
            "Config update failed: ${e.message}"
        }
    }

    suspend fun claimOrphan(
        config: AuraPayConfig,
        idempotencyKey: String,
        orderId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(
                mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "orderId" to orderId,
                    "claimedBy" to "AuraPay Mobile Operator"
                )
            )
            val url = "${config.serverUrl.trimEnd('/')}/api/relay/orphans/claim"
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
