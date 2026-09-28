package com.codex.quota

import com.codex.quota.data.remote.isCloudflareNodeBlock
import okhttp3.Headers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareNodeBlockTest {
    private val challengeHeaders = Headers.Builder()
        .add("Content-Type", "text/html; charset=UTF-8")
        .add("CF-RAY", "example")
        .build()

    @Test fun cloudflareHtml403_isNodeBlock() {
        assertTrue(isCloudflareNodeBlock(403, challengeHeaders))
    }

    @Test fun ordinaryForbiddenOrSuccessfulResponse_doesNotPromptForNodeSwitch() {
        assertFalse(isCloudflareNodeBlock(403, Headers.Builder().add("Content-Type", "application/json").build()))
        assertFalse(isCloudflareNodeBlock(403, Headers.Builder().add("Content-Type", "text/html").build()))
        assertFalse(isCloudflareNodeBlock(200, challengeHeaders))
    }
}
