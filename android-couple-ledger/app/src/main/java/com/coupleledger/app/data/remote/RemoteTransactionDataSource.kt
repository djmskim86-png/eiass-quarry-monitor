package com.coupleledger.app.data.remote

import com.coupleledger.app.data.local.entity.TransactionEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로컬(Room)의 거래 내역을 Firestore로 밀어 올리고(push),
 * 배우자 기기가 올린 새 내역을 받아온다(pull).
 * 문서 id를 TransactionEntity.id(결정적 해시)로 그대로 쓰기 때문에
 * 같은 거래를 여러 번 push해도 덮어쓰기만 될 뿐 중복 문서가 생기지 않는다.
 */
@Singleton
class RemoteTransactionDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun collection(householdId: String) = firestore
        .collection(FirestoreSchema.HOUSEHOLDS)
        .document(householdId)
        .collection(FirestoreSchema.TRANSACTIONS)

    suspend fun push(householdId: String, transactions: List<TransactionEntity>) {
        if (transactions.isEmpty()) return
        val batch = firestore.batch()
        val col = collection(householdId)
        transactions.forEach { tx ->
            batch.set(col.document(tx.id), tx.toRemoteMap())
        }
        batch.commit().await()
    }

    /** since 이후( > )에 발생한 거래만 가져온다. 매 동기화 주기마다 마지막 pull 시각을 기준으로 호출. */
    suspend fun pullSince(householdId: String, sinceOccurredAt: Long): List<TransactionEntity> {
        val snapshot = collection(householdId)
            .whereGreaterThan(FirestoreSchema.FIELD_OCCURRED_AT, sinceOccurredAt)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toTransactionEntityOrNull(householdId) }
    }
}
