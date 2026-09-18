package com.coupleledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coupleledger.app.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE householdId = :householdId AND isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveCards(householdId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE householdId = :householdId")
    suspend fun getAllForHousehold(householdId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE issuerCode = :issuerCode AND householdId = :householdId AND isActive = 1 LIMIT 1")
    suspend fun findByIssuer(householdId: String, issuerCode: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)

    @Update
    suspend fun update(card: CardEntity)

    @Query("UPDATE cards SET isActive = 0, updatedAt = :now WHERE id = :cardId")
    suspend fun deactivate(cardId: String, now: Long = System.currentTimeMillis())
}
