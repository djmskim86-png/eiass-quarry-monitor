package com.coupleledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 등록한 카드 한 장.
 * issuerCode는 [com.coupleledger.app.sms.parser.CardIssuer]와 매칭되어
 * 문자 파싱 규칙을 결정하고, nickname은 사용자가 화면에서 직접 붙이는 이름이다.
 * (예: issuerCode="KB_CARD", nickname="국민카드(생활비)")
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val ownerUserId: String,
    val issuerCode: String,
    val nickname: String,
    val last4Digits: String? = null,
    val colorHex: String = "#4C6FFF",
    val isActive: Boolean = true,
    val monthlyBudget: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
