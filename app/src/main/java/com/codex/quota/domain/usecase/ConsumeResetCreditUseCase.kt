package com.codex.quota.domain.usecase

import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.data.remote.ResetCreditCode
import com.codex.quota.data.remote.ResetCreditConsumer
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.ResetOperationStore
import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ResetSpendOutcome {
    data class Success(val freshUsage: CodexUsage?, val alreadyRedeemed: Boolean) : ResetSpendOutcome
    data object NoCredit : ResetSpendOutcome
    data object NothingToReset : ResetSpendOutcome
    data object Uncertain : ResetSpendOutcome
    data object Unavailable : ResetSpendOutcome
}

class ConsumeResetCreditUseCase(
    private val repository: CodexAccountRepository,
    private val credentialStore: CredentialStore,
    private val operationStore: ResetOperationStore,
    private val consumer: ResetCreditConsumer
) {
    private val mutex = Mutex()

    suspend operator fun invoke(accountId: String): ResetSpendOutcome = mutex.withLock {
        val accountWithUsage = repository.getAccount(accountId) ?: return@withLock ResetSpendOutcome.Unavailable
        if (accountWithUsage.account.isDemoAccount || (accountWithUsage.usage?.bankedResets ?: 0) <= 0) {
            return@withLock ResetSpendOutcome.Unavailable
        }
        val token = credentialStore.getApiKey(accountId)?.takeIf { it.isNotBlank() }
            ?: return@withLock ResetSpendOutcome.Unavailable
        val chatgptAccountId = JwtTokenParser.parseToken(token)?.chatgptAccountId
            ?: accountWithUsage.account.organizationId
            ?: return@withLock ResetSpendOutcome.Unavailable
        if (chatgptAccountId.isBlank()) return@withLock ResetSpendOutcome.Unavailable

        // Persist before dispatch. An ambiguous response can only be retried with this same ID.
        val operationId = try {
            operationStore.getOrCreateResetOperationId(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withLock ResetSpendOutcome.Unavailable
        }
        val result = consumer.consume(token, chatgptAccountId, operationId)
        when (result) {
            ResetCreditCode.RESET, ResetCreditCode.ALREADY_REDEEMED -> {
                val freshUsage = repository.refreshAccount(accountId).getOrNull()
                // If refresh fails, keep the operation ID so a second tap cannot spend another credit.
                if (freshUsage != null) operationStore.clearResetOperationId(accountId, operationId)
                ResetSpendOutcome.Success(freshUsage, result == ResetCreditCode.ALREADY_REDEEMED)
            }
            ResetCreditCode.NO_CREDIT -> {
                operationStore.clearResetOperationId(accountId, operationId)
                repository.refreshAccount(accountId)
                ResetSpendOutcome.NoCredit
            }
            ResetCreditCode.NOTHING_TO_RESET -> {
                operationStore.clearResetOperationId(accountId, operationId)
                repository.refreshAccount(accountId)
                ResetSpendOutcome.NothingToReset
            }
            ResetCreditCode.UNKNOWN -> ResetSpendOutcome.Uncertain
        }
    }
}
