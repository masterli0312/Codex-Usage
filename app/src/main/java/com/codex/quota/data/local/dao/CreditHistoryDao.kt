package com.codex.quota.data.local.dao

import androidx.room.*
import com.codex.quota.data.local.entity.CreditHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditHistoryDao {
    @Query("SELECT * FROM credit_history WHERE accountId = :accountId ORDER BY observedAtEpochMs DESC, id DESC LIMIT 200")
    fun observe(accountId: String): Flow<List<CreditHistoryEntity>>
    @Query("SELECT * FROM credit_history WHERE accountId = :accountId ORDER BY observedAtEpochMs DESC, id DESC LIMIT 1")
    suspend fun latest(accountId: String): CreditHistoryEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE id = :accountId)")
    suspend fun accountExists(accountId: String): Boolean
    @Insert suspend fun insert(entry: CreditHistoryEntity)
    @Query("DELETE FROM credit_history WHERE accountId = :accountId AND id NOT IN (SELECT id FROM credit_history WHERE accountId = :accountId ORDER BY observedAtEpochMs DESC, id DESC LIMIT 200)")
    suspend fun prune(accountId: String)

    @Transaction
    suspend fun record(accountId: String, balance: Double, observedAt: Long) {
        if (!balance.isFinite() || balance < 0 || observedAt <= 0 || !accountExists(accountId)) return
        val previous = latest(accountId)
        if (previous != null && (observedAt <= previous.observedAtEpochMs || balance == previous.balance)) return
        insert(CreditHistoryEntity(accountId = accountId, balance = balance,
            observedAtEpochMs = observedAt, change = previous?.let { balance - it.balance }))
        prune(accountId)
    }
}
