package com.codex.quota.notifications.task

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class NtfyTaskClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).build()) {
    fun events(endpoint: String, since: String, onConnected: () -> Unit) = callbackFlow {
        require(TaskNotificationProtocol.normalizeEndpoint(endpoint) != null)
        val url = endpoint.toHttpUrl().newBuilder().addPathSegment("json").addQueryParameter("since", since).build()
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/x-ndjson").build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) { close(IOException("Notification service unavailable")); return }
                    val source = response.body?.source() ?: run { close(); return }
                    onConnected()
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8LineStrict(32_768)
                            val event = TaskNotificationProtocol.decode(line) ?: continue
                            // Backpressure must reconnect from the persisted cursor, never silently drop events.
                            if (!trySend(event).isSuccess) { close(IOException("Notification queue full")); return }
                        }
                        close()
                    } catch (e: IOException) { close(e) }
                }
            }
        })
        awaitClose { call.cancel() }
    }
}
