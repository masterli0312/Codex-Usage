package com.codex.quota.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class ResetCreditCode { RESET, ALREADY_REDEEMED, NO_CREDIT, NOTHING_TO_RESET, UNKNOWN }

interface ResetCreditConsumer {
    suspend fun consume(accessToken: String, chatgptAccountId: String, operationId: String): ResetCreditCode
}

class WhamResetCreditConsumer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) : ResetCreditConsumer {
    override suspend fun consume(accessToken: String, chatgptAccountId: String, operationId: String): ResetCreditCode =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("redeem_request_id", operationId) }
                .toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://chatgpt.com/backend-api/wham/rate-limit-reset-credits/consume")
                .header("Authorization", "Bearer " + accessToken.trim().removePrefix("Bearer ").removePrefix("bearer "))
                .header("ChatGPT-Account-Id", chatgptAccountId)
                .header("Accept", "application/json")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ResetCreditCode.UNKNOWN
                    val code = Json.parseToJsonElement(response.peekBody(64 * 1024).string())
                        .jsonObject["code"]?.jsonPrimitive?.content
                    when (code) {
                        "reset" -> ResetCreditCode.RESET
                        "already_redeemed" -> ResetCreditCode.ALREADY_REDEEMED
                        "no_credit" -> ResetCreditCode.NO_CREDIT
                        "nothing_to_reset" -> ResetCreditCode.NOTHING_TO_RESET
                        else -> ResetCreditCode.UNKNOWN
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ResetCreditCode.UNKNOWN
            }
        }
}
