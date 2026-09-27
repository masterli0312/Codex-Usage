package com.codex.quota.domain.repository

interface FiveHourActivationStore {
    suspend fun claimFiveHourActivation(accountId: String, resetAtEpochMs: Long): Boolean
}
