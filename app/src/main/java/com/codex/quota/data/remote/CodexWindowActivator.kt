package com.codex.quota.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A single small inference, following the completion check used by opencodex's warmup flow. */
class CodexWindowActivator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun activate(accessToken: String, chatgptAccountId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val models = listOf("gpt-5.6-luna", "gpt-5.5")
            for ((index, model) in models.withIndex()) {
                val result = runCatching { sendOneRequest(accessToken, chatgptAccountId, model) }
                if (result.isSuccess) return@withContext Result.success(Unit)
                val failure = result.exceptionOrNull()
                // A rejected model cannot consume quota. Never retry an uncertain stream result.
                if (failure !is ModelUnavailableException || index == models.lastIndex) {
                    return@withContext Result.failure(failure ?: IllegalStateException("Activation failed"))
                }
            }
            Result.failure(IllegalStateException("No supported Codex model"))
        }

    private fun sendOneRequest(accessToken: String, chatgptAccountId: String, model: String) {
        val input = JSONArray().put(JSONObject()
            .put("type", "message")
            .put("role", "user")
            .put("content", JSONArray().put(JSONObject()
                .put("type", "input_text")
                .put("text", "hi"))))
        val payload = JSONObject()
            .put("model", model)
            .put("instructions", "Reply with OK.")
            .put("input", input)
            .put("stream", true)
            .put("store", false)
        val request = Request.Builder()
            .url("https://chatgpt.com/backend-api/codex/responses")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("ChatGPT-Account-Id", chatgptAccountId)
            .header("Accept", "text/event-stream")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 400 || response.code == 404) throw ModelUnavailableException()
            if (!response.isSuccessful) throw IllegalStateException("Codex activation rejected: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Codex activation response is empty")
            body.charStream().buffered().use { reader ->
                var consumed = 0
                val data = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    consumed += line.length + 1
                    if (consumed > MAX_RESPONSE_CHARS) throw IllegalStateException("Codex activation response too large")
                    if (line.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trimStart())
                    } else if (line.isBlank() && data.isNotEmpty()) {
                        if (isCompleted(data.toString())) return
                        data.clear()
                    }
                }
                if (data.isNotEmpty() && isCompleted(data.toString())) return
            }
        }
        throw IllegalStateException("Codex activation did not complete")
    }

    private fun isCompleted(data: String): Boolean {
        if (data == "[DONE]") return false
        val type = JSONObject(data).optString("type")
        if (type == "response.completed") return true
        if (type == "response.failed" || type == "response.incomplete" || type == "error") {
            throw IllegalStateException("Codex activation stream failed")
        }
        return false
    }

    private class ModelUnavailableException : Exception()

    companion object {
        private const val MAX_RESPONSE_CHARS = 1_048_576
    }
}
