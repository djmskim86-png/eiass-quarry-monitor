package com.coupleledger.app.sms

import com.coupleledger.app.data.local.entity.TransactionEntity
import com.coupleledger.app.data.local.entity.TransactionSource
import com.coupleledger.app.data.remote.HouseholdSession
import com.coupleledger.app.data.repository.CardRepository
import com.coupleledger.app.data.repository.TransactionRepository
import com.coupleledger.app.sms.parser.SmsParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 문자 한 통을 실제 거래 내역으로 확정 저장하는 공통 로직.
 * 실시간 리시버(ProcessSmsWorker)와 주기 스캔(PeriodicSmsScanWorker) 양쪽에서 재사용한다.
 *
 * 등록되지 않은 카드사의 문자는 무시한다(사용자가 "카드 추가"에서 먼저 등록해야
 * 자동 인식이 시작됨 — 결제 문자와 광고/인증 문자를 구분하는 1차 필터 역할도 겸함).
 */
@Singleton
class SmsTransactionRecorder @Inject constructor(
    private val cardRepository: CardRepository,
    private val transactionRepository: TransactionRepository,
    private val householdSession: HouseholdSession,
) {
    suspend fun tryRecord(sender: String, body: String, receivedAt: Long): Boolean {
        val parsed = SmsParser.parse(sender, body, receivedAt) ?: return false
        val card = cardRepository.findCardForIssuer(parsed.issuerCode) ?: return false
        val householdId = householdSession.householdIdOrNull() ?: return false

        val transaction = TransactionEntity(
            id = TransactionIdGenerator.generate(card.id, parsed.occurredAt, parsed.amount, parsed.merchant),
            householdId = householdId,
            cardId = card.id,
            spentByUserId = card.ownerUserId,
            amount = parsed.amount,
            merchant = parsed.merchant,
            occurredAt = parsed.occurredAt,
            isCancellation = parsed.isCancellation,
            source = TransactionSource.SMS_AUTO,
            rawSmsBody = parsed.rawBody,
        )
        transactionRepository.insertParsed(transaction)
        return true
    }
}
