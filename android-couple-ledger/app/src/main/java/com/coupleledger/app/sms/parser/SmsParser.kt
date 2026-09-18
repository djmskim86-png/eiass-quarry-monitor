package com.coupleledger.app.sms.parser

import java.util.Calendar

data class ParsedCardSms(
    val issuerCode: String,
    val amount: Long,
    val merchant: String?,
    val occurredAt: Long,
    val isCancellation: Boolean,
    val rawBody: String,
)

/**
 * 문자 한 통(발신자 + 본문)을 카드 승인 내역으로 변환한다.
 * 매칭되는 카드사 규칙이 없거나(광고/인증 문자 등) 금액 추출에 실패하면 null을 반환한다.
 *
 * receivedAt은 문자 수신 시각(fallback)이며, 본문에 거래 일시(MM/dd HH:mm)가 있으면
 * 그 값으로 occurredAt을 보정한다(문자가 지연 도착하는 경우 대비).
 */
object SmsParser {

    fun parse(sender: String, body: String, receivedAt: Long): ParsedCardSms? {
        val rule = CardIssuerRules.findBySender(sender) ?: return null

        val amountMatch = rule.amountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toLongOrNull() ?: return null

        val merchant = rule.merchantRegex?.find(body)?.groupValues?.getOrNull(1)?.trim()
        val isCancellation = rule.cancelKeywords.any { body.contains(it) }
        val occurredAt = resolveOccurredAt(rule, body, receivedAt)

        return ParsedCardSms(
            issuerCode = rule.code,
            amount = amount,
            merchant = merchant?.ifBlank { null },
            occurredAt = occurredAt,
            isCancellation = isCancellation,
            rawBody = body,
        )
    }

    private fun resolveOccurredAt(rule: CardIssuerRule, body: String, receivedAt: Long): Long {
        val match = rule.dateTimeRegex?.find(body) ?: return receivedAt
        val (month, day, hour, minute) = match.destructured
        return runCatching {
            Calendar.getInstance().apply {
                timeInMillis = receivedAt
                set(Calendar.MONTH, month.toInt() - 1)
                set(Calendar.DAY_OF_MONTH, day.toInt())
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrDefault(receivedAt)
    }

}
