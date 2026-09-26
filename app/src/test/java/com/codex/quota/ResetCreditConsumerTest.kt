package com.codex.quota

import com.codex.quota.data.remote.ResetCreditCode
import com.codex.quota.data.remote.WhamResetCreditConsumer
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetCreditConsumerTest {
    @Test
    fun consume_sendsAccountAndStableOperationId() = runBlocking {
        var path = ""
        var accountHeader = ""
        var body = ""
        val consumer = consumer(200, """{"code":"reset"}""") { request ->
            path = request.url.encodedPath
            accountHeader = request.header("ChatGPT-Account-Id").orEmpty()
            body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        }

        val result = consumer.consume("token", "account-123", "operation-123")

        assertEquals(ResetCreditCode.RESET, result)
        assertEquals("/backend-api/wham/rate-limit-reset-credits/consume", path)
        assertEquals("account-123", accountHeader)
        assertEquals("""{"redeem_request_id":"operation-123"}""", body)
    }

    @Test
    fun consume_doesNotTreatUnexpectedResponseAsSuccess() = runBlocking {
        assertEquals(ResetCreditCode.UNKNOWN, consumer(200, """{"code":"unexpected"}""").consume("token", "account", "operation"))
        assertEquals(ResetCreditCode.UNKNOWN, consumer(503, """{"error":"busy"}""").consume("token", "account", "operation"))
    }

    private fun consumer(status: Int, response: String, inspect: (okhttp3.Request) -> Unit = {}): WhamResetCreditConsumer {
        val interceptor = Interceptor { chain ->
            inspect(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("fixture")
                .body(response.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return WhamResetCreditConsumer(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }
}
