package com.coupleledger.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.coupleledger.app.data.local.dao.CardDao
import com.coupleledger.app.data.local.dao.TransactionDao
import com.coupleledger.app.data.local.entity.CardEntity
import com.coupleledger.app.data.local.entity.TransactionEntity

@Database(
    entities = [CardEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val DB_NAME = "our_ledger.db"
    }
}
