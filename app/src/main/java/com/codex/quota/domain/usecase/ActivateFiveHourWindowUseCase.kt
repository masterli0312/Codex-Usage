package com.codex.quota.domain.usecase

import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.data.remote.CodexWindowActivator
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.FiveHourActivationStore
import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.CancellationException

sealed interface FiveHourActivationOutcome {
    data class Success(val freshUsage: CodexUsage?) : FiveHourActivationOutcome
    data object NotDue : FiveHourActivationOutcome
    data object LoginRequired : FiveHourActivationOutcome
    data object AlreadyAttempted : FiveHourActivationOutcome
    data object RefreshFailed : FiveHourActivationOutcome
    data object Uncertain : FiveHourActivationOutcome
}

/** Manual and background activation share the same durable, one-attempt-per-window guard. */
class ActivateFiveHourWindowUseCase(
    private val repository: CodexAccountRepository,
    private val credentialStore: CredentialStore,
    private val operationStore: FiveHourActivationStore,
    private val activator: CodexWindowActivator
) {
    suspend operator fun invoke(
        accountId: String,
        resetAtEpochMs: Long,
        allowActivation: suspend () -> Boolean = { true }
    ): FiveHourActivationOutcome {
        val account = repository.getAccount(accountId) ?: return FiveHourActivationOutcome.NotDue
        val now = System.currentTimeMillis()
        if (account.account.isDemoAccount || resetAtEpochMs <= 0L || resetAtEpochMs > now) {
            return FiveHourActivationOutcome.NotDue
        }

        val refreshed = repository.refreshAccount(accountId)
        if (refreshed.isFailure) return FiveHourActivationOutcome.RefreshFailed
        val usageBeforeActivation = refreshed.getOrThrow()
        if (usageBeforeActivation.status == AuthStatus.AUTHENTICATION_REQUIRED) {
            return FiveHourActivationOutcome.LoginRequired
        }
        // The repository can return cached quota with OFFLINE/TEMPORARY_ERROR as a successful Result.
        // No activation was sent, so this preflight remains safe to retry when the network recovers.
        if (usageBeforeActivation.status != AuthStatus.AUTHENTICATED) {
            return FiveHourActivationOutcome.RefreshFailed
        }
        if (usageBeforeActivation.isWeeklyQuotaExhausted) return FiveHourActivationOutcome.NotDue
        // A newer reset timestamp means this window has already rolled over and started.
        if (usageBeforeActivation.fiveHourResetAtEpochMs?.let { it > resetAtEpochMs } == true) {
            return FiveHourActivationOutcome.NotDue
        }

        val token = credentialStore.getApiKey(accountId) ?: return FiveHourActivationOutcome.LoginRequired
        val chatgptAccountId = JwtTokenParser.parseToken(token)?.chatgptAccountId
            ?: return FiveHourActivationOutcome.LoginRequired
        if (!allowActivation()) return FiveHourActivationOutcome.NotDue
        val claimed = try {
            operationStore.claimFiveHourActivation(accountId, resetAtEpochMs)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return FiveHourActivationOutcome.RefreshFailed
        }
        if (!claimed) {
            return FiveHourActivationOutcome.AlreadyAttempted
        }

        // Claim is persisted before network I/O. A timeout may have reached the server.
        val activation = try {
            activator.activate(token, chatgptAccountId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Codex activation failed"))
        }
        val freshUsage = repository.refreshAccount(accountId).getOrNull()
        return if (activation.isSuccess) FiveHourActivationOutcome.Success(freshUsage)
        else FiveHourActivationOutcome.Uncertain
    }
}
