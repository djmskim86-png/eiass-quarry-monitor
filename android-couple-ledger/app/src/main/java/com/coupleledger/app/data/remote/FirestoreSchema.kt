package com.coupleledger.app.data.remote

/**
 * Firestore 컬렉션 경로 상수 모음.
 *
 * households/{householdId}
 *   - name, inviteCode, memberUserIds: [uid], createdAt
 * households/{householdId}/cards/{cardId}
 *   - CardEntity와 동일한 필드
 * households/{householdId}/transactions/{transactionId}
 *   - TransactionEntity와 동일한 필드(문서 id = TransactionIdGenerator 결과 그대로 사용 → 중복 push 방지)
 *
 * 보안 규칙(概略): households/{id} 문서의 memberUserIds에 request.auth.uid가
 * 포함된 사용자만 그 하위 컬렉션을 읽고 쓸 수 있도록 제한한다(파이어스토어 콘솔에서 설정).
 */
object FirestoreSchema {
    const val HOUSEHOLDS = "households"
    const val CARDS = "cards"
    const val TRANSACTIONS = "transactions"

    const val FIELD_MEMBER_USER_IDS = "memberUserIds"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_OCCURRED_AT = "occurredAt"
}
