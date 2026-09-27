package com.codex.quota.data.repository

import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.auth.OAuthManager
import com.codex.quota.auth.OAuthTokenRefreshRejectedException
import com.codex.quota.data.local.dao.AccountDao
import com.codex.quota.data.local.dao.UsageSnapshotDao
import com.codex.quota.data.local.entity.AccountEntity
import com.codex.quota.data.local.entity.UsageSnapshotEntity
import com.codex.quota.data.remote.CodexAccountDataSource
import com.codex.quota.data.remote.MockOpenAiDataSource
import com.codex.quota.data.remote.RealOpenAiDataSource
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class CodexAccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val usageSnapshotDao: UsageSnapshotDao,
    private val credentialStore: CredentialStore,
    private val realDataSource: CodexAccountDataSource = RealOpenAiDataSource(),
    private val mockDataSource: CodexAccountDataSource = MockOpenAiDataSource(),
    private val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onUsageRefreshed: (CodexUsage) -> Unit = {}
) : CodexAccountRepository {

    private val refreshMutex = Mutex()
    private val tokenRefreshMutex = Mutex()
    private var activeRefresh: Deferred<Result<List<CodexUsage>>>? = null

    override fun observeAccounts(): Flow<List<AccountWithUsage>> {
        return combine(
            accountDao.observeAll(),
            usageSnapshotDao.observeAll()
        ) { accounts, snapshots ->
            val snapshotMap = snapshots.associateBy { it.accountId }
            accounts.map { accountEntity ->
                val domainAccount = accountEntity.toDomain()
                val domainUsage = snapshotMap[accountEntity.id]?.toDomain()
                AccountWithUsage(domainAccount, domainUsage)
            }
        }
    }

    override fun observeAccount(accountId: String): Flow<AccountWithUsage?> {
        return combine(
            accountDao.observeById(accountId),
            usageSnapshotDao.observeByAccountId(accountId)
        ) { accountEntity, snapshotEntity ->
            if (accountEntity == null) null
            else {
                AccountWithUsage(
                    account = accountEntity.toDomain(),
                    usage = snapshotEntity?.toDomain()
                )
            }
        }
    }

    override suspend fun getAccount(accountId: String): AccountWithUsage? {
        val accountEntity = accountDao.getById(accountId) ?: return null
        val snapshotEntity = usageSnapshotDao.getByAccountId(accountId)
        return AccountWithUsage(
            account = accountEntity.toDomain(),
            usage = snapshotEntity?.toDomain()
        )
    }

    override suspend fun getAllAccounts(): List<AccountWithUsage> {
        val accounts = accountDao.getAll()
        val snapshots = usageSnapshotDao.getAll().associateBy { it.accountId }
        return accounts.map {
            AccountWithUsage(it.toDomain(), snapshots[it.id]?.toDomain())
        }
    }

    override suspend fun addAccount(
        nickname: String,
        email: String?,
        apiKey: String,
        planType: PlanType,
        organizationId: String?,
        colorHex: String,
        isDemoAccount: Boolean,
        oauthRefreshToken: String?,
        oauthClientId: String?
    ): Result<CodexAccount> {
        val accountId = UUID.randomUUID().toString()
        val existing = accountDao.getAll()
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val now = System.currentTimeMillis()

        val domainAccount = CodexAccount(
            id = accountId,
            nickname = nickname,
            email = email,
            planType = planType,
            organizationId = organizationId,
            colorHex = colorHex,
            authStatus = AuthStatus.REFRESHING,
            isDemoAccount = isDemoAccount,
            orderIndex = nextOrder,
            createdAtEpochMs = now,
            lastSuccessfulSyncEpochMs = null
        )

        if (!oauthRefreshToken.isNullOrBlank() && !oauthClientId.isNullOrBlank()) {
            credentialStore.storeOAuthTokens(accountId, apiKey, oauthRefreshToken, oauthClientId)
        } else {
            credentialStore.storeApiKey(accountId, apiKey)
        }
        accountDao.insert(AccountEntity.fromDomain(domainAccount))

        // Trigger initial refresh
        val refreshResult = refreshAccountInternal(domainAccount, apiKey)
        return if (refreshResult.isSuccess) {
            val updatedAccount = accountDao.getById(accountId)?.toDomain() ?: domainAccount
            Result.success(updatedAccount)
        } else {
            Result.success(domainAccount)
        }
    }

    override suspend fun updateAccount(
        accountId: String,
        nickname: String,
        colorHex: String,
        customRenewalDateEpochMs: Long?
    ): Result<Unit> {
        accountDao.updateDetails(accountId, nickname, colorHex, customRenewalDateEpochMs)
        return Result.success(Unit)
    }

    override suspend fun setAccountRenewalDate(
        accountId: String,
        renewalDateEpochMs: Long?
    ): Result<Unit> {
        accountDao.updateRenewalDate(accountId, renewalDateEpochMs)
        return Result.success(Unit)
    }

    override suspend fun reauthenticateAccount(
        accountId: String,
        newApiKey: String
    ): Result<CodexUsage> {
        val account = accountDao.getById(accountId)?.toDomain()
            ?: return Result.failure(IllegalArgumentException("Account $accountId not found"))

        credentialStore.storeApiKey(accountId, newApiKey)
        return refreshAccountInternal(account, newApiKey)
    }

    override suspend fun removeAccount(accountId: String): Result<Unit> {
        credentialStore.removeApiKey(accountId)
        usageSnapshotDao.deleteByAccountId(accountId)
        accountDao.deleteById(accountId)
        return Result.success(Unit)
    }

    override suspend fun reorderAccounts(accountIdsInOrder: List<String>): Result<Unit> {
        accountDao.updateOrderIndices(accountIdsInOrder)
        return Result.success(Unit)
    }

    override suspend fun refreshAccount(accountId: String): Result<CodexUsage> {
        val account = accountDao.getById(accountId)?.toDomain()
            ?: return Result.failure(IllegalArgumentException("Account $accountId not found"))
        val apiKey = credentialStore.getApiKey(accountId).orEmpty()
        return refreshAccountInternal(account, apiKey)
    }

    private suspend fun refreshAccountInternal(
        account: CodexAccount,
        apiKey: String
    ): Result<CodexUsage> {
        val dataSource = if (account.isDemoAccount) mockDataSource else realDataSource
        val currentKey = if (account.isDemoAccount) Result.success(apiKey)
            else ensureFreshAccessToken(account.id, apiKey)
        if (currentKey.isFailure) return Result.failure(currentKey.exceptionOrNull()!!)
        val result = dataSource.fetchUsage(account, currentKey.getOrThrow())

        return if (result.isSuccess) {
            val usage = result.getOrThrow()
            usageSnapshotDao.insertOrUpdate(UsageSnapshotEntity.fromDomain(usage))
            if (!account.isDemoAccount && usage.status == AuthStatus.AUTHENTICATED) {
                onUsageRefreshed(usage)
            }

            val lastSync = if (usage.status == AuthStatus.AUTHENTICATED) {
                usage.fetchedAtEpochMs
            } else {
                account.lastSuccessfulSyncEpochMs
            }

            accountDao.updateAuthStatusAndSyncTime(
                accountId = account.id,
                authStatus = usage.status.name,
                lastSync = lastSync
            )
            Result.success(usage)
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Unknown sync error")
            val offlineUsage = CodexUsage.empty(account.id, AuthStatus.TEMPORARY_ERROR)
            Result.failure(exception)
        }
    }

    private suspend fun ensureFreshAccessToken(accountId: String, suppliedKey: String): Result<String> =
        tokenRefreshMutex.withLock {
            val storedKey = credentialStore.getApiKey(accountId) ?: suppliedKey
            val expiry = JwtTokenParser.parseToken(storedKey)?.expiresAtEpochMs
                ?: return@withLock Result.success(storedKey)
            if (expiry > System.currentTimeMillis() + 5 * 60_000L) {
                return@withLock Result.success(storedKey)
            }
            val refreshToken = credentialStore.getRefreshToken(accountId)
                ?: return@withLock Result.success(storedKey)
            val clientId = credentialStore.getOAuthClientId(accountId)
                ?: return@withLock Result.failure(IllegalStateException("OAuth client identifier missing"))
            val refreshedResult = OAuthManager.refreshAccessToken(refreshToken, clientId)
            val rejection = refreshedResult.exceptionOrNull() as? OAuthTokenRefreshRejectedException
            if (rejection != null && rejection.statusCode in listOf(400, 401) &&
                expiry <= System.currentTimeMillis()) {
                // Let the existing expired-token path mark the account as needing login.
                return@withLock Result.success(storedKey)
            }
            refreshedResult.map { refreshed ->
                credentialStore.storeOAuthTokens(
                    accountId,
                    refreshed.accessToken,
                    refreshed.refreshToken ?: refreshToken,
                    clientId
                )
                refreshed.accessToken
            }
        }

    override suspend fun refreshAllAccounts(): Result<List<CodexUsage>> {
        val refresh = refreshMutex.withLock {
            activeRefresh?.takeIf { it.isActive } ?: refreshScope.async {
                refreshAllAccountsInternal()
            }.also { activeRefresh = it }
        }

        return try {
            refresh.await()
        } finally {
            refreshMutex.withLock {
                if (activeRefresh === refresh && refresh.isCompleted) {
                    activeRefresh = null
                }
            }
        }
    }

    private suspend fun refreshAllAccountsInternal(): Result<List<CodexUsage>> = coroutineScope {
        val accounts = accountDao.getAll()
        val concurrencyLimit = Semaphore(MAX_CONCURRENT_ACCOUNT_REFRESHES)
        val results = accounts.map { account ->
            async {
                concurrencyLimit.withPermit {
                    try {
                        val domainAccount = account.toDomain()
                        val apiKey = credentialStore.getApiKey(domainAccount.id).orEmpty()
                        refreshAccountInternal(domainAccount, apiKey).getOrNull()
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll()
        Result.success(results.filterNotNull())
    }

    override suspend fun clearAllData(): Result<Unit> {
        credentialStore.clearAll()
        usageSnapshotDao.deleteAll()
        accountDao.deleteAll()
        return Result.success(Unit)
    }

    private companion object {
        const val MAX_CONCURRENT_ACCOUNT_REFRESHES = 4
    }
}
