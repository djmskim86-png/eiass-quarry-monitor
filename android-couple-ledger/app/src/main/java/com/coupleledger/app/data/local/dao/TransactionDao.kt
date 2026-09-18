package com.coupleledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coupleledger.app.data.local.entity.CardMonthlyTotal
import com.coupleledger.app.data.local.entity.MemberMonthlyTotal
import com.coupleledger.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE householdId = :householdId ORDER BY occurredAt DESC")
    fun observeAll(householdId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE householdId = :householdId AND isCancellation = 0
          AND occurredAt BETWEEN :monthStart AND :monthEndExclusive
        """
    )
    fun observeMonthlyTotal(householdId: String, monthStart: Long, monthEndExclusive: Long): Flow<Long>

    @Query(
        """
        SELECT cardId, COALESCE(SUM(amount), 0) as total FROM transactions
        WHERE householdId = :householdId AND isCancellation = 0
          AND occurredAt BETWEEN :monthStart AND :monthEndExclusive
        GROUP BY cardId
        """
    )
    fun observeMonthlyTotalByCard(
        householdId: String,
        monthStart: Long,
        monthEndExclusive: Long,
    ): Flow<List<CardMonthlyTotal>>

    @Query(
        """
        SELECT spentByUserId, COALESCE(SUM(amount), 0) as total FROM transactions
        WHERE householdId = :householdId AND isCancellation = 0
          AND occurredAt BETWEEN :monthStart AND :monthEndExclusive
        GROUP BY spentByUserId
        """
    )
    fun observeMonthlyTotalByMember(
        householdId: String,
        monthStart: Long,
        monthEndExclusive: Long,
    ): Flow<List<MemberMonthlyTotal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING' ORDER BY occurredAt ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int = 200): List<TransactionEntity>

    @Update
    suspend fun update(item: TransactionEntity)

    @Query("SELECT MAX(occurredAt) FROM transactions WHERE householdId = :householdId")
    suspend fun getLatestOccurredAt(householdId: String): Long?
}
