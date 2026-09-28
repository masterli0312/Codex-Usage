package com.codex.quota.data.remote

import okhttp3.Headers

const val CF_BLOCKED_ERROR = "CLOUDFLARE_NODE_BLOCKED"

internal fun isCloudflareNodeBlock(httpCode: Int, headers: Headers): Boolean =
    httpCode == 403 && headers["CF-RAY"] != null &&
        headers["Content-Type"]?.contains("text/html", ignoreCase = true) == true
