package com.codex.quota.ui.navigation

/** Only account notification URIs are accepted; OAuth callbacks have their own handler. */
internal fun accountIdFromNotificationLink(link: String?): String? {
    if (link == null) return null
    val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return null
    if (uri.scheme != "codexquota" || uri.host != "account" || uri.query != null || uri.fragment != null) return null
    return uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() && it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
}
