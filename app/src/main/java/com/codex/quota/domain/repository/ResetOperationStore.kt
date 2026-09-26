package com.codex.quota.domain.repository

interface ResetOperationStore {
    suspend fun getOrCreateResetOperationId(accountId: String): String
    suspend fun clearResetOperationId(accountId: String, operationId: String)
}
