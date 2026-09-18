package com.coupleledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionSource { SMS_AUTO, MANUAL }
enum class SyncStatus { PENDING, SYNCED, FAILED }

/**
 * 지출 내역 한 건. id는 (cardId + occurredAt + amount + merchant)를 해시한
 * 결정적(deterministic) 값으로 만들어, 같은 문자를 실시간 리시버와
 * 주기 스캔 워커가 중복으로 처리해도 같은 id가 나와 insert가 자동으로 무시되게 한다.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val cardId: String,
    val spentByUserId: String,
    val amount: Long,
    val merchant: String?,
    val occurredAt: Long,
    val memo: String? = null,
    val category: String? = null,
    val isCancellation: Boolean = false,
    val source: TransactionSource = TransactionSource.SMS_AUTO,
    val rawSmsBody: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
)

data class CardMonthlyTotal(val cardId: String, val total: Long)
data class MemberMonthlyTotal(val spentByUserId: String, val total: Long)
