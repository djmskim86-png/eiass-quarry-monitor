package com.coupleledger.app.sms

import java.security.MessageDigest

/**
 * 같은 문자(같은 카드+시각+금액+가맹점)가 실시간 리시버와 주기 스캔 워커
 * 양쪽에서 처리되더라도 항상 같은 id가 나오게 하는 결정적 해시.
 * Room insert가 OnConflictStrategy.IGNORE이므로 중복 저장을 자연스럽게 막아준다.
 */
object TransactionIdGenerator {
    fun generate(cardId: String, occurredAt: Long, amount: Long, merchant: String?): String {
        val raw = "$cardId|$occurredAt|$amount|${merchant.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
