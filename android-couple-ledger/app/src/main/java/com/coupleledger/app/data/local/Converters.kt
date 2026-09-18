package com.coupleledger.app.data.local

import androidx.room.TypeConverter
import com.coupleledger.app.data.local.entity.SyncStatus
import com.coupleledger.app.data.local.entity.TransactionSource

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")

    @TypeConverter
    fun fromSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
