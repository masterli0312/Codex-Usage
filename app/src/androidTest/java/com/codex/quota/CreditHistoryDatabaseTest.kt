package com.codex.quota

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.codex.quota.data.local.AppDatabase
import com.codex.quota.data.local.entity.AccountEntity
import com.codex.quota.data.local.entity.UsageSnapshotEntity
import com.codex.quota.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditHistoryDatabaseTest {
    @Test fun migrationPreservesAccountsAndHistoryIsIsolatedBoundedAndCascades() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "credit_history_migration_test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_7_8).build()
        val account = CodexAccount(id = "fixture-a", nickname = "fixture", email = null,
            planType = PlanType.PLUS, organizationId = null, colorHex = "#10B981",
            authStatus = AuthStatus.AUTHENTICATED, isDemoAccount = false, orderIndex = 0,
            createdAtEpochMs = 1, lastSuccessfulSyncEpochMs = 100, customRenewalDateEpochMs = 123456)
        var database = open()
        try {
            database.accountDao().insert(AccountEntity.fromDomain(account))
            val usage = CodexUsage.empty(account.id, AuthStatus.AUTHENTICATED).copy(remainingCredits = 3000.0)
            database.usageSnapshotDao().insertOrUpdate(UsageSnapshotEntity.fromDomain(usage))
            database.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE credit_history")
                it.version = 7
            }
            database = open()
            assertEquals(account, database.accountDao().getById(account.id)?.toDomain())
            assertEquals(3000.0, database.usageSnapshotDao().getByAccountId(account.id)!!.toDomain().remainingCredits!!, 0.0)
            val history = database.creditHistoryDao()
            assertNull(history.latest(account.id))
            database.accountDao().insert(AccountEntity.fromDomain(account.copy(id = "fixture-b")))
            history.record("fixture-b", 99.0, 1)
            repeat(205) { history.record(account.id, 3000.0 - it, it + 1L) }
            val count = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM credit_history WHERE accountId = 'fixture-a'").use {
                it.moveToFirst(); it.getInt(0)
            }
            assertEquals(200, count)
            history.record(account.id, 2796.0, 300)
            val afterUnchanged = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM credit_history WHERE accountId = 'fixture-a'").use {
                it.moveToFirst(); it.getInt(0)
            }
            assertEquals(200, afterUnchanged)
            database.accountDao().deleteById(account.id)
            assertNull(history.latest(account.id))
            assertEquals(99.0, history.latest("fixture-b")!!.balance, 0.0)
            database.accountDao().deleteAll()
            assertNull(history.latest("fixture-b"))
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
