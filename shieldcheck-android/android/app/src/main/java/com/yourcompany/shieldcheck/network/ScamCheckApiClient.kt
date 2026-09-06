package com.yourcompany.shieldcheck.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class ScamCheckRequest(
    val device_id: String,
    val type: String, // "text" | "image" | "call_transcript"
    val content: String,
    val source_app: String? = null,
)

@Serializable
data class ScamCheckResponse(
    val request_id: String,
    val verdict: String, // "likely_scam" | "suspicious" | "likely_safe"
    val confidence: Double,
    val reasons: List<String>,
    val recommended_action: String,
)

@Serializable
data class MalwareReportRequest(
    val device_id: String,
    val package_name: String,
    val reason: String,
)

@Serializable
data class MalwareReportResponse(
    val report_count: Int,
    val widely_reported: Boolean,
)

/**
 * Update baseUrl to wherever you deploy backend/main.py.
 * Points at localhost:8000 by default for emulator testing
 * (10.0.2.2 is how the Android emulator reaches your host machine).
 */
class ScamCheckApiClient(
    private val baseUrl: String = "http://10.0.2.2:8000",
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkContent(request: ScamCheckRequest): Result<ScamCheckResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body =
                    json.encodeToString(request)
                        .toRequestBody("application/json".toMediaType())

                val httpRequest =
                    Request.Builder()
                        .url("$baseUrl/v1/scam-check")
                        .post(body)
                        .build()

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        return@withContext Result.failure(
                            IOException("Server error: ${response.code}"),
                        )
                    }
                    Result.success(json.decodeFromString(responseBody))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Best-effort — a malware finding should still show on-screen or
     * notify even if this fails, so callers should log/ignore failures
     * rather than surface them. This is what makes findings cross-device
     * instead of trapped on whichever phone found them first.
     */
    suspend fun reportMalwareFinding(request: MalwareReportRequest): Result<MalwareReportResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body =
                    json.encodeToString(request)
                        .toRequestBody("application/json".toMediaType())

                val httpRequest =
                    Request.Builder()
                        .url("$baseUrl/v1/malware-report")
                        .post(body)
                        .build()

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        return@withContext Result.failure(
                            IOException("Server error: ${response.code}"),
                        )
                    }
                    Result.success(json.decodeFromString(responseBody))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
