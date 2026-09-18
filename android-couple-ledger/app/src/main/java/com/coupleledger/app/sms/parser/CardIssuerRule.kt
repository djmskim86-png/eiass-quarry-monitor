package com.coupleledger.app.sms.parser

/**
 * 카드사 1곳의 승인 문자 형식을 정의하는 규칙.
 *
 * 실제 문자 포맷은 카드사가 수시로 바꾸므로, 아래 정규식들은 "출발점"이다.
 * 새 카드를 등록했는데 자동 인식이 안 되면 설정 > "인식 안 된 문자 신고"에서
 * 원문을 수집해 이 파일의 regex만 수정하면 된다(다른 레이어를 건드릴 필요 없음).
 *
 * senderContains: 발신번호 또는 발신자 표시명에 포함되는 문자열(여러 개 중 하나만 맞아도 매칭).
 * cancelKeywords: 문자 본문에 포함되면 "취소/환불" 거래로 표시(합계에서 자동 차감).
 */
data class CardIssuerRule(
    val code: String,
    val displayName: String,
    val senderContains: List<String>,
    val cancelKeywords: List<String> = listOf("취소", "환불", "승인취소"),
    val amountRegex: Regex,
    val merchantRegex: Regex?,
    val dateTimeRegex: Regex?,
)

object CardIssuerRules {

    // 국민카드: "[KB국민카드] 승인 김*수 12,000원 일시불 09/12 14:23 스타벅스코리아"
    private val kb = CardIssuerRule(
        code = "KB_CARD",
        displayName = "국민카드",
        senderContains = listOf("KB국민카드", "국민카드", "15881688"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}/\d{2}\s+\d{2}:\d{2}\s+(.+)$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    // 신한카드: "[신한카드] 승인 12,000원 일시불 09/12 14:23 스타벅스 홍길동(1234)"
    private val shinhan = CardIssuerRule(
        code = "SHINHAN_CARD",
        displayName = "신한카드",
        senderContains = listOf("신한카드", "15448000"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}:\d{2}\s+(.+?)(\s+\S+\(\d+\))?$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    // 삼성카드: "[삼성카드] 승인 12,000원(09/12 14:23) 스타벅스"
    private val samsung = CardIssuerRule(
        code = "SAMSUNG_CARD",
        displayName = "삼성카드",
        senderContains = listOf("삼성카드", "1588970"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\)\s*(.+)$"""),
        dateTimeRegex = Regex("""\((\d{2})/(\d{2})\s+(\d{2}):(\d{2})\)"""),
    )

    // 현대카드: "[현대카드] 승인 이*민 12,000원 일시불 09/12 14:23 스타벅스"
    private val hyundai = CardIssuerRule(
        code = "HYUNDAI_CARD",
        displayName = "현대카드",
        senderContains = listOf("현대카드", "15774000"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}:\d{2}\s+(.+)$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    // 우리카드: "[우리카드] 승인 12,000원 09/12 14:23 스타벅스"
    private val woori = CardIssuerRule(
        code = "WOORI_CARD",
        displayName = "우리카드",
        senderContains = listOf("우리카드", "15887000"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}:\d{2}\s+(.+)$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    // 하나카드: "[하나카드] 승인 12,000원 09/12 14:23 스타벅스"
    private val hana = CardIssuerRule(
        code = "HANA_CARD",
        displayName = "하나카드",
        senderContains = listOf("하나카드", "18001111"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}:\d{2}\s+(.+)$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    // 롯데카드: "[롯데카드] 승인 12,000원 09/12 14:23 스타벅스"
    private val lotte = CardIssuerRule(
        code = "LOTTE_CARD",
        displayName = "롯데카드",
        senderContains = listOf("롯데카드", "15881688"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}:\d{2}\s+(.+)$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    // NH농협카드/체크카드: "[NH농협카드] 승인 12,000원 09/12 14:23 스타벅스"
    private val nh = CardIssuerRule(
        code = "NH_CARD",
        displayName = "NH농협카드",
        senderContains = listOf("NH농협카드", "NH농협", "15448100"),
        amountRegex = Regex("""([0-9,]+)\s*원"""),
        merchantRegex = Regex("""\d{2}:\d{2}\s+(.+)$"""),
        dateTimeRegex = Regex("""(\d{2})/(\d{2})\s+(\d{2}):(\d{2})"""),
    )

    val defaults: List<CardIssuerRule> = listOf(kb, shinhan, samsung, hyundai, woori, hana, lotte, nh)

    fun findBySender(sender: String): CardIssuerRule? =
        defaults.firstOrNull { rule -> rule.senderContains.any { sender.contains(it) } }

    fun findByCode(code: String): CardIssuerRule? = defaults.firstOrNull { it.code == code }
}
