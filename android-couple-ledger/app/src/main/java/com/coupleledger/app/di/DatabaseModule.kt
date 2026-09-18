package com.coupleledger.app.di

import android.content.Context
import androidx.room.Room
import com.coupleledger.app.data.local.AppDatabase
import com.coupleledger.app.data.local.dao.CardDao
import com.coupleledger.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME).build()

    @Provides
    fun provideCardDao(db: AppDatabase): CardDao = db.cardDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
}
