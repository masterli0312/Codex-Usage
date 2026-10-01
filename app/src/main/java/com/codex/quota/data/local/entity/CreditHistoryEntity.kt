package com.codex.quota.data.local.entity

import androidx.room.*

@Entity(tableName = "credit_history", foreignKeys = [ForeignKey(entity = AccountEntity::class,
    parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["accountId", "observedAtEpochMs"])])
data class CreditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val balance: Double,
    val observedAtEpochMs: Long,
    val change: Double?
)
