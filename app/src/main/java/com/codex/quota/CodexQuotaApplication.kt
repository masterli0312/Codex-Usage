package com.codex.quota

import android.app.Application
import android.net.Uri
import com.codex.quota.data.local.AppDatabase
import com.codex.quota.data.local.DataStoreManager
import com.codex.quota.data.remote.MockOpenAiDataSource
import com.codex.quota.data.remote.RealOpenAiDataSource
import com.codex.quota.data.remote.WhamResetCreditConsumer
import com.codex.quota.data.repository.CodexAccountRepositoryImpl
import com.codex.quota.data.repository.UserPreferencesRepositoryImpl
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.UserPreferencesRepository
import com.codex.quota.domain.usecase.ConsumeResetCreditUseCase
import com.codex.quota.domain.usecase.ActivateFiveHourWindowUseCase
import com.codex.quota.data.remote.CodexWindowActivator
import com.codex.quota.security.EncryptedCredentialStore
import com.codex.quota.security.KeystoreManager
import com.codex.quota.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CodexQuotaApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var currentOAuthCallbackUri: Uri? = null

    lateinit var database: AppDatabase
        private set

    lateinit var credentialStore: EncryptedCredentialStore
        private set

    lateinit var dataStoreManager: DataStoreManager
        private set

    lateinit var repository: CodexAccountRepository
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var consumeResetCredit: ConsumeResetCreditUseCase
        private set

    lateinit var activateFiveHourWindow: ActivateFiveHourWindowUseCase
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        val keystoreManager = KeystoreManager(this)
        credentialStore = EncryptedCredentialStore(this, keystoreManager)
        dataStoreManager = DataStoreManager(this)

        repository = CodexAccountRepositoryImpl(
            accountDao = database.accountDao(),
            usageSnapshotDao = database.usageSnapshotDao(),
            credentialStore = credentialStore,
            realDataSource = RealOpenAiDataSource(),
            mockDataSource = MockOpenAiDataSource(),
            refreshScope = applicationScope,
            onUsageRefreshed = { usage ->
                applicationScope.launch {
                    val prefs = preferencesRepository.getPreferences()
                    if (prefs.fiveHourResetReminderEnabled) {
                        WorkScheduler.scheduleFiveHourResetReminder(
                            this@CodexQuotaApplication, usage.accountId, usage.fiveHourResetAtEpochMs
                        )
                    }
                    if (prefs.backgroundSyncEnabled) {
                        WorkScheduler.scheduleFiveHourResetRefresh(
                            this@CodexQuotaApplication,
                            usage.accountId,
                            usage.fiveHourResetAtEpochMs
                        )
                    }
                }
            }
        )

        preferencesRepository = UserPreferencesRepositoryImpl(dataStoreManager)
        consumeResetCredit = ConsumeResetCreditUseCase(repository, credentialStore, dataStoreManager, WhamResetCreditConsumer())
        activateFiveHourWindow = ActivateFiveHourWindowUseCase(
            repository, credentialStore, dataStoreManager, CodexWindowActivator()
        )

        // Restore one-time reset refreshes from persisted official window timestamps.
        applicationScope.launch {
            val prefs = preferencesRepository.getPreferences()
            if (prefs.fiveHourResetReminderEnabled) {
                repository.getAllAccounts().forEach { item ->
                    if (!item.account.isDemoAccount) {
                        WorkScheduler.scheduleFiveHourResetReminder(
                            this@CodexQuotaApplication, item.account.id,
                            item.usage?.fiveHourResetAtEpochMs
                        )
                    }
                }
            } else {
                WorkScheduler.cancelFiveHourResetReminders(this@CodexQuotaApplication)
            }
            if (prefs.backgroundSyncEnabled) {
                WorkScheduler.schedulePeriodicRefresh(this@CodexQuotaApplication, prefs.refreshInterval.minutes)
                repository.getAllAccounts().forEach { item ->
                    if (!item.account.isDemoAccount) {
                        WorkScheduler.scheduleFiveHourResetRefresh(
                            this@CodexQuotaApplication,
                            item.account.id,
                            item.usage?.fiveHourResetAtEpochMs
                        )
                    }
                }
            } else {
                WorkScheduler.cancelPeriodicRefresh(this@CodexQuotaApplication)
                WorkScheduler.cancelFiveHourResetRefresh(this@CodexQuotaApplication)
            }
        }
    }

    fun markOnboardingComplete() {
        applicationScope.launch {
            preferencesRepository.setHasCompletedOnboarding(true)
        }
    }
}
