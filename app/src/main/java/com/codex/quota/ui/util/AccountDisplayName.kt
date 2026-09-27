package com.codex.quota.ui.util

import android.content.Context
import com.codex.quota.R
import com.codex.quota.domain.model.CodexAccount

/** Removes the plan prefix generated for older accounts without changing custom nicknames. */
fun displayAccountNickname(nickname: String, fullPlanName: String, email: String?, fallback: String): String {
    val prefix = "$fullPlanName ("
    if (nickname.startsWith(prefix) && nickname.endsWith(')')) {
        val name = nickname.substring(prefix.length, nickname.length - 1).trim()
        if (name.isNotEmpty()) return name
    }
    if (nickname == fullPlanName) {
        return email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: fallback
    }
    return nickname
}

fun localizedAccountNickname(context: Context, account: CodexAccount): String = displayAccountNickname(
    account.nickname,
    localizedPlanName(context, account.planType),
    account.email,
    context.getString(R.string.default_account_nickname)
)
