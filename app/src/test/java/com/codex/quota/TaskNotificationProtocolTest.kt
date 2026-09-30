package com.codex.quota

import com.codex.quota.notifications.task.TaskNotificationProtocol
import com.codex.quota.notifications.task.TaskNotificationStore
import org.junit.Assert.*
import org.junit.Test

class TaskNotificationProtocolTest {
    @Test fun reconnectReplaysCursorBoundaryWithoutFetchingBeforeInitialOptIn() {
        assertEquals("1700000000", TaskNotificationStore.replaySince(null, 1700000000L))
        assertEquals("1700000000", TaskNotificationStore.replaySince("1700000001", 1700000000L))
        assertEquals("0", TaskNotificationStore.replaySince("0", 1700000000L))
    }

    @Test fun onlyHttpsTopicConnectionsAreAccepted() {
        assertEquals("https://ntfy.sh/codex-usage-example", TaskNotificationProtocol.normalizeEndpoint(" https://ntfy.sh/codex-usage-example "))
        for (url in listOf("http://ntfy.sh/topic", "https://user:password@ntfy.sh/topic", "https://ntfy.sh/", "https://ntfy.sh/a/b", "https://ntfy.sh/topic?token=secret", "https://ntfy.sh/topic#fragment")) {
            assertNull(TaskNotificationProtocol.normalizeEndpoint(url))
        }
    }

    @Test fun codexCompletionIsDecodedWithoutConversationContent() {
        val event = TaskNotificationProtocol.decode("""{"id":"ntfy-123","event":"message","time":1700000000,"message":"{\"schema_version\":\"1.0\",\"agent_source\":\"codex\",\"status\":\"turn_complete\",\"session_id\":\"thread-1\",\"turn_id\":\"turn-1\",\"message\":\"private conversation\"}"}""")
        assertNotNull(event)
        assertEquals("ntfy-123", event!!.id)
        assertEquals("turn_complete", event.status)
        assertEquals(1700000000000L, event.timeEpochMs)
        assertEquals("thread-1:turn-1", event.deduplicationId)
    }

    @Test fun agentNotificationsCodexWebhookIsCompatible() {
        val event = TaskNotificationProtocol.decode("""{"id":"ntfy-456","event":"message","time":1700000000,"message":"{\"schema_version\":\"1.0\",\"agent_source\":\"codex\",\"status\":\"task_complete\"}"}""")
        assertEquals("task_complete", event?.status)
    }

    @Test fun malformedControlAndForeignEventsAreIgnored() {
        for (line in listOf("invalid", "{}", """{"event":"keepalive"}""", """{"id":"x","event":"message","time":1,"message":"hello"}""", envelope("claude", "task_complete"), envelope("codex", "unknown_status"), envelope("codex", "turn_complete", "2.0"))) {
            assertNull(TaskNotificationProtocol.decode(line))
        }
    }

    private fun envelope(agent: String, status: String, version: String = "1.0") =
        """{"id":"x","event":"message","time":1,"message":"{\"schema_version\":\"$version\",\"agent_source\":\"$agent\",\"status\":\"$status\"}"}"""
}
