package com.coupleledger.app.data.remote

import com.coupleledger.app.data.local.entity.CardEntity
import com.coupleledger.app.data.local.entity.SyncStatus
import com.coupleledger.app.data.local.entity.TransactionEntity
import com.coupleledger.app.data.local.entity.TransactionSource
import com.google.firebase.firestore.DocumentSnapshot

fun TransactionEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "cardId" to cardId,
    "spentByUserId" to spentByUserId,
    "amount" to amount,
    "merchant" to merchant,
    "occurredAt" to occurredAt,
    "memo" to memo,
    "category" to category,
    "isCancellation" to isCancellation,
    "source" to source.name,
    "rawSmsBody" to rawSmsBody,
    "createdAt" to createdAt,
)

fun DocumentSnapshot.toTransactionEntityOrNull(householdId: String): TransactionEntity? {
    val cardId = getString("cardId") ?: return null
    val spentByUserId = getString("spentByUserId") ?: return null
    val amount = getLong("amount") ?: return null
    val occurredAt = getLong("occurredAt") ?: return null
    return TransactionEntity(
        id = id,
        householdId = householdId,
        cardId = cardId,
        spentByUserId = spentByUserId,
        amount = amount,
        merchant = getString("merchant"),
        occurredAt = occurredAt,
        memo = getString("memo"),
        category = getString("category"),
        isCancellation = getBoolean("isCancellation") ?: false,
        source = runCatching { TransactionSource.valueOf(getString("source") ?: "MANUAL") }
            .getOrDefault(TransactionSource.MANUAL),
        rawSmsBody = getString("rawSmsBody"),
        syncStatus = SyncStatus.SYNCED,
        createdAt = getLong("createdAt") ?: occurredAt,
    )
}

fun CardEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "ownerUserId" to ownerUserId,
    "issuerCode" to issuerCode,
    "nickname" to nickname,
    "last4Digits" to last4Digits,
    "colorHex" to colorHex,
    "isActive" to isActive,
    "monthlyBudget" to monthlyBudget,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun DocumentSnapshot.toCardEntityOrNull(householdId: String): CardEntity? {
    val ownerUserId = getString("ownerUserId") ?: return null
    val issuerCode = getString("issuerCode") ?: return null
    val nickname = getString("nickname") ?: return null
    return CardEntity(
        id = id,
        householdId = householdId,
        ownerUserId = ownerUserId,
        issuerCode = issuerCode,
        nickname = nickname,
        last4Digits = getString("last4Digits"),
        colorHex = getString("colorHex") ?: "#4C6FFF",
        isActive = getBoolean("isActive") ?: true,
        monthlyBudget = getLong("monthlyBudget"),
        createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
        updatedAt = getLong("updatedAt") ?: System.currentTimeMillis(),
    )
}
