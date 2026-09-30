package com.codex.quota.notifications.task

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class TaskCompletionEvent(val id: String, val status: String, val timeEpochMs: Long, val deduplicationId: String)

/** ntfy transports metadata only; assistant text is deliberately never displayed or stored. */
object TaskNotificationProtocol {
    private val statuses = setOf("turn_complete", "task_complete", "review_complete", "question", "plan_ready", "api_error", "api_error_overloaded", "session_limit_reached", "permission_request", "test")

    fun normalizeEndpoint(value: String): String? {
        val url = value.trim().toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.pathSegments.size != 1) return null
        val topic = url.pathSegments.single()
        if (!topic.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return null
        return url.toString()
    }

    fun decode(line: String): TaskCompletionEvent? = runCatching {
        if (line.length > 32_768) return null
        val envelope = Json.parseToJsonElement(line).jsonObject
        if (envelope["event"]?.jsonPrimitive?.content != "message") return null
        val id = envelope["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        val time = envelope["time"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1000L } ?: return null
        val payload = Json.parseToJsonElement(envelope["message"]?.jsonPrimitive?.content ?: return null).jsonObject
        if (payload["schema_version"]?.jsonPrimitive?.content != "1.0" || payload["agent_source"]?.jsonPrimitive?.content != "codex") return null
        val status = payload["status"]?.jsonPrimitive?.content?.takeIf { it in statuses } ?: return null
        val session = payload["session_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it.length <= 128 }
        val turn = payload["turn_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it.length <= 128 }
        TaskCompletionEvent(id, status, time * 1000L, if (session != null && turn != null) "$session:$turn" else id)
    }.getOrNull()
}
