package com.codex.quota

import com.codex.quota.data.local.dao.AccountDao
import com.codex.quota.data.local.dao.UsageSnapshotDao
import com.codex.quota.data.local.entity.AccountEntity
import com.codex.quota.data.local.entity.UsageSnapshotEntity
import com.codex.quota.data.remote.MockOpenAiDataSource
import com.codex.quota.data.remote.CodexAccountDataSource
import com.codex.quota.data.repository.CodexAccountRepositoryImpl
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodexAccountRepositoryTest {

    private lateinit var fakeAccountDao: FakeAccountDao
    private lateinit var fakeUsageDao: FakeUsageSnapshotDao
    private lateinit var fakeCredentialStore: FakeCredentialStore
    private lateinit var repository: CodexAccountRepositoryImpl

    @Before
    fun setUp() {
        fakeAccountDao = FakeAccountDao()
        fakeUsageDao = FakeUsageSnapshotDao()
        fakeCredentialStore = FakeCredentialStore()
        repository = CodexAccountRepositoryImpl(
            accountDao = fakeAccountDao,
            usageSnapshotDao = fakeUsageDao,
            credentialStore = fakeCredentialStore,
            realDataSource = MockOpenAiDataSource(),
            mockDataSource = MockOpenAiDataSource()
        )
    }

    @Test
    fun addAccount_persistsAccountAndCredentials() = runTest {
        val result = repository.addAccount(
            nickname = "Primary Key",
            email = "user@test.org",
            apiKey = "sk-test-12345",
            planType = PlanType.PLUS,
            organizationId = "org-123",
            colorHex = "#10B981",
            isDemoAccount = true
        )

        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals("Primary Key", account.nickname)

        // Verify stored in credentials
        val storedKey = fakeCredentialStore.getApiKey(account.id)
        assertEquals("sk-test-12345", storedKey)

        // Verify saved in DAO
        val savedEntity = fakeAccountDao.getById(account.id)
        assertNotNull(savedEntity)
        assertEquals("Primary Key", savedEntity?.nickname)
    }

    @Test
    fun oauthAccount_persistsRefreshCredentialAndRemovesItWithAccount() = runTest {
        val account = repository.addAccount(
            nickname = "OAuth",
            email = null,
            apiKey = "access-token",
            planType = PlanType.PLUS,
            organizationId = null,
            colorHex = "#10B981",
            isDemoAccount = false,
            oauthRefreshToken = "refresh-token",
            oauthClientId = "official-client"
        ).getOrThrow()

        assertEquals("access-token", fakeCredentialStore.getApiKey(account.id))
        assertEquals("refresh-token", fakeCredentialStore.getRefreshToken(account.id))
        assertEquals("official-client", fakeCredentialStore.getOAuthClientId(account.id))

        repository.removeAccount(account.id)
        assertNull(fakeCredentialStore.getApiKey(account.id))
        assertNull(fakeCredentialStore.getRefreshToken(account.id))
        assertNull(fakeCredentialStore.getOAuthClientId(account.id))
    }

    @Test
    fun removeAccount_cleansUpDatabaseAndCredentials() = runTest {
        val addResult = repository.addAccount(
            nickname = "To Delete",
            email = null,
            apiKey = "sk-delete-me",
            planType = PlanType.PLUS,
            organizationId = null,
            colorHex = "#EF4444",
            isDemoAccount = true
        )
        val accountId = addResult.getOrThrow().id

        assertNotNull(fakeCredentialStore.getApiKey(accountId))

        val removeResult = repository.removeAccount(accountId)
        assertTrue(removeResult.isSuccess)

        assertNull(fakeCredentialStore.getApiKey(accountId))
        assertNull(fakeAccountDao.getById(accountId))
    }

    @Test
    fun updateAccount_updatesNicknameAndColor() = runTest {
        val addResult = repository.addAccount(
            nickname = "Original",
            email = null,
            apiKey = "sk-update",
            planType = PlanType.PLUS,
            organizationId = null,
            colorHex = "#10B981",
            isDemoAccount = true
        )
        val accountId = addResult.getOrThrow().id

        repository.updateAccount(accountId, "New Nickname", "#38BDF8")

        val updated = fakeAccountDao.getById(accountId)
        assertEquals("New Nickname", updated?.nickname)
        assertEquals("#38BDF8", updated?.colorHex)
    }

    @Test
    fun rename_preservesSubscriptionAndQuotaAndCredentials() = runTest {
        val account = repository.addAccount("old", null, "test-key", PlanType.PLUS, null, "#10B981", true).getOrThrow()
        repository.setAccountRenewalDate(account.id, 123456L)
        val before = repository.getAccount(account.id)!!
        assertTrue(repository.renameAccount(account.id, "  new  ").isSuccess)
        val after = repository.getAccount(account.id)!!
        assertEquals("new", after.account.nickname)
        assertEquals(before.account.copy(nickname = "new"), after.account)
        assertEquals(before.usage, after.usage)
        assertEquals("test-key", fakeCredentialStore.getApiKey(account.id))
        assertTrue(repository.renameAccount(account.id, "  ").isFailure)
    }

    @Test
    fun reorder_preservesNewAccountsAndRejectsDuplicates() = runTest {
        val a = repository.addAccount("a", null, "key-a", PlanType.PLUS, null, "#10B981", true).getOrThrow()
        val b = repository.addAccount("b", null, "key-b", PlanType.PLUS, null, "#10B981", true).getOrThrow()
        val c = repository.addAccount("c", null, "key-c", PlanType.PLUS, null, "#10B981", true).getOrThrow()
        repository.reorderAccounts(listOf(b.id, a.id))
        assertEquals(listOf(b.id, a.id, c.id), repository.getAllAccounts().map { it.account.id })
        val invalid = runCatching { repository.reorderAccounts(listOf(a.id, a.id)) }
        assertTrue(invalid.isFailure || invalid.getOrThrow().isFailure)
        assertEquals(listOf(b.id, a.id, c.id), repository.getAllAccounts().map { it.account.id })
    }

    @Test
    fun creditHistory_usesOnlySuccessfulRealProviderBalances() = runTest {
        var status = AuthStatus.AUTHENTICATED
        val source = object : CodexAccountDataSource {
            override suspend fun fetchUsage(account: CodexAccount, apiKey: String) = Result.success(
                CodexUsage.empty(account.id, status).copy(remainingCredits = 100.0))
        }
        val history = io.mockk.mockk<com.codex.quota.data.local.dao.CreditHistoryDao>(relaxed = true)
        val repo = CodexAccountRepositoryImpl(fakeAccountDao, fakeUsageDao, fakeCredentialStore,
            realDataSource = source, mockDataSource = source, creditHistoryDao = history)
        val real = repo.addAccount("real", null, "key", PlanType.PLUS, null, "#10B981", false).getOrThrow()
        io.mockk.coVerify(exactly = 1) { history.record(real.id, 100.0, any()) }
        status = AuthStatus.OFFLINE
        repo.refreshAccount(real.id)
        io.mockk.coVerify(exactly = 1) { history.record(any(), any(), any()) }
        status = AuthStatus.AUTHENTICATED
        repo.addAccount("demo", null, "key", PlanType.PLUS, null, "#10B981", true)
        io.mockk.coVerify(exactly = 1) { history.record(any(), any(), any()) }
    }

    @Test
    fun temporaryFailure_keepsLastSuccessfulQuotaUntilNextSuccess() = runTest {
        var status = AuthStatus.AUTHENTICATED
        var percent = 70.0
        val source = object : CodexAccountDataSource {
            override suspend fun fetchUsage(account: CodexAccount, apiKey: String): Result<CodexUsage> =
                Result.success(CodexUsage(
                    accountId = account.id, remainingPercent = if (status == AuthStatus.AUTHENTICATED) percent else null,
                    usedPercent = null, usedTokens = null, totalLimitTokens = null,
                    remainingCredits = if (status == AuthStatus.AUTHENTICATED) 3.0 else null,
                    resetAtEpochMs = if (status == AuthStatus.AUTHENTICATED) 999_999_999_999L else null,
                    status = status, fetchedAtEpochMs = if (status == AuthStatus.AUTHENTICATED) 100L else 200L,
                    bankedResets = if (status == AuthStatus.AUTHENTICATED) 2 else null,
                    fiveHourRemainingPercent = if (status == AuthStatus.AUTHENTICATED) 80.0 else null,
                    errorMessage = if (status == AuthStatus.AUTHENTICATED) null else "HTTP 503"
                ))
        }
        val repository = CodexAccountRepositoryImpl(fakeAccountDao, fakeUsageDao, fakeCredentialStore,
            realDataSource = source, mockDataSource = source)
        val account = repository.addAccount("li", null, "mock", PlanType.PLUS, null, "#10B981", true).getOrThrow()

        status = AuthStatus.TEMPORARY_ERROR
        val failed = repository.refreshAccount(account.id).getOrThrow()
        assertEquals(AuthStatus.TEMPORARY_ERROR, failed.status)
        assertEquals(70.0, failed.remainingPercent!!, 0.0)
        assertEquals(80.0, failed.fiveHourRemainingPercent!!, 0.0)
        assertEquals(3.0, failed.remainingCredits!!, 0.0)
        assertEquals(2, failed.bankedResets)
        assertEquals(100L, failed.fetchedAtEpochMs)
        assertEquals("HTTP 503", failed.errorMessage)
        assertEquals(100L, repository.getAccount(account.id)?.account?.lastSuccessfulSyncEpochMs)

        status = AuthStatus.AUTHENTICATED
        percent = 45.0
        val recovered = repository.refreshAccount(account.id).getOrThrow()
        assertEquals(AuthStatus.AUTHENTICATED, recovered.status)
        assertEquals(45.0, recovered.remainingPercent!!, 0.0)
        assertNull(recovered.errorMessage)
    }
}

// In-Memory Fakes for deterministic unit testing
class FakeAccountDao : AccountDao {
    private val accounts = MutableStateFlow<Map<String, AccountEntity>>(emptyMap())

    override fun observeAll(): Flow<List<AccountEntity>> =
        accounts.map { it.values.sortedBy { a -> a.orderIndex } }

    override fun observeById(accountId: String): Flow<AccountEntity?> =
        accounts.map { it[accountId] }

    override suspend fun getById(accountId: String): AccountEntity? =
        accounts.value[accountId]

    override suspend fun getAll(): List<AccountEntity> =
        accounts.value.values.sortedBy { it.orderIndex }

    override suspend fun insert(account: AccountEntity) {
        accounts.value = accounts.value + (account.id to account)
    }

    override suspend fun update(account: AccountEntity) {
        accounts.value = accounts.value + (account.id to account)
    }

    override suspend fun updateDetails(accountId: String, nickname: String, colorHex: String, customRenewalDateEpochMs: Long?) {
        val existing = accounts.value[accountId] ?: return
        accounts.value = accounts.value + (accountId to existing.copy(nickname = nickname, colorHex = colorHex, customRenewalDateEpochMs = customRenewalDateEpochMs))
    }

    override suspend fun updateNicknameAndColor(accountId: String, nickname: String, colorHex: String) {
        val existing = accounts.value[accountId] ?: return
        accounts.value = accounts.value + (accountId to existing.copy(nickname = nickname, colorHex = colorHex))
    }

    override suspend fun updateRenewalDate(accountId: String, renewalDateEpochMs: Long?) {
        val existing = accounts.value[accountId] ?: return
        accounts.value = accounts.value + (accountId to existing.copy(customRenewalDateEpochMs = renewalDateEpochMs))
    }

    override suspend fun updateAuthStatusAndSyncTime(accountId: String, authStatus: String, lastSync: Long?) {
        val existing = accounts.value[accountId] ?: return
        accounts.value = accounts.value + (accountId to existing.copy(authStatus = authStatus, lastSuccessfulSyncEpochMs = lastSync))
    }

    override suspend fun deleteById(accountId: String) {
        accounts.value = accounts.value - accountId
    }

    override suspend fun deleteAll() {
        accounts.value = emptyMap()
    }

    override suspend fun updateOrderIndex(id: String, orderIndex: Int) {
        val existing = accounts.value[id] ?: return
        accounts.value = accounts.value + (id to existing.copy(orderIndex = orderIndex))
    }
}

class FakeUsageSnapshotDao : UsageSnapshotDao {
    private val snapshots = MutableStateFlow<Map<String, UsageSnapshotEntity>>(emptyMap())

    override fun observeAll(): Flow<List<UsageSnapshotEntity>> =
        snapshots.map { it.values.toList() }

    override fun observeByAccountId(accountId: String): Flow<UsageSnapshotEntity?> =
        snapshots.map { it[accountId] }

    override suspend fun getByAccountId(accountId: String): UsageSnapshotEntity? =
        snapshots.value[accountId]

    override suspend fun getAll(): List<UsageSnapshotEntity> =
        snapshots.value.values.toList()

    override suspend fun insertOrUpdate(snapshot: UsageSnapshotEntity) {
        snapshots.value = snapshots.value + (snapshot.accountId to snapshot)
    }

    override suspend fun deleteByAccountId(accountId: String) {
        snapshots.value = snapshots.value - accountId
    }

    override suspend fun deleteAll() {
        snapshots.value = emptyMap()
    }
}

class FakeCredentialStore : CredentialStore {
    private val storage = mutableMapOf<String, String>()
    private val refreshTokens = mutableMapOf<String, String>()
    private val clientIds = mutableMapOf<String, String>()

    override fun storeApiKey(accountId: String, apiKey: String) {
        storage[accountId] = apiKey
        refreshTokens.remove(accountId)
        clientIds.remove(accountId)
    }

    override fun storeOAuthTokens(accountId: String, accessToken: String, refreshToken: String, clientId: String) {
        storage[accountId] = accessToken
        refreshTokens[accountId] = refreshToken
        clientIds[accountId] = clientId
    }

    override fun getRefreshToken(accountId: String): String? = refreshTokens[accountId]

    override fun getOAuthClientId(accountId: String): String? = clientIds[accountId]

    override fun getApiKey(accountId: String): String? = storage[accountId]

    override fun removeApiKey(accountId: String) {
        storage.remove(accountId)
        refreshTokens.remove(accountId)
        clientIds.remove(accountId)
    }

    override fun clearAll() {
        storage.clear()
        refreshTokens.clear()
        clientIds.clear()
    }
}
