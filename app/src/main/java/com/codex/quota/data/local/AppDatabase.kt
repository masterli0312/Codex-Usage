package com.codex.quota.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codex.quota.data.local.dao.CreditHistoryDao
import com.codex.quota.data.local.entity.CreditHistoryEntity
import com.codex.quota.data.local.dao.AccountDao
import com.codex.quota.data.local.dao.UsageSnapshotDao
import com.codex.quota.data.local.entity.AccountEntity
import com.codex.quota.data.local.entity.UsageSnapshotEntity

@Database(
    entities = [
        AccountEntity::class,
        UsageSnapshotEntity::class,
        CreditHistoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun creditHistoryDao(): CreditHistoryDao
    abstract fun accountDao(): AccountDao
    abstract fun usageSnapshotDao(): UsageSnapshotDao

    companion object {
        private const val DATABASE_NAME = "codex_quota_db"

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE accounts ADD COLUMN customRenewalDayOfMonth INTEGER")
                database.execSQL("ALTER TABLE accounts ADD COLUMN customRenewalDateEpochMs INTEGER")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN subscriptionRenewalEpochMs INTEGER")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN subscriptionStartedAtEpochMs INTEGER")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN billingPeriod TEXT")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN accountCreatedEpochMs INTEGER")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN willAutoRenew INTEGER")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN hasActiveSubscription INTEGER")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN bankedResets INTEGER")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN bankedResetExpiresAtEpochMs INTEGER")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN fiveHourRemainingPercent REAL")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN fiveHourUsedPercent REAL")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN fiveHourResetAtEpochMs INTEGER")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN gptReserveRemainingPercent REAL")
                database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN gptReserveResetAtEpochMs INTEGER")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS credit_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId TEXT NOT NULL, balance REAL NOT NULL, observedAtEpochMs INTEGER NOT NULL, change REAL, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_credit_history_accountId_observedAtEpochMs ON credit_history(accountId, observedAtEpochMs)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
