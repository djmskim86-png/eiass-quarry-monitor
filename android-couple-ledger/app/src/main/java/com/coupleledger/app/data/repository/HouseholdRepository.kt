package com.coupleledger.app.data.repository

import com.coupleledger.app.data.remote.FirestoreSchema
import com.coupleledger.app.data.remote.HouseholdSession
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 가구(부부) 생성/참여를 담당.
 * 한쪽이 "새 가계부 만들기"로 6자리 초대 코드를 발급하면, 배우자가 그 코드를 입력해
 * 같은 householdId에 합류한다. 이후 모든 카드/거래는 이 householdId 아래에 공유된다.
 */
@Singleton
class HouseholdRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    suspend fun createHousehold(name: String, ownerUserId: String): String {
        val doc = firestore.collection(FirestoreSchema.HOUSEHOLDS).document()
        val inviteCode = generateInviteCode()
        doc.set(
            mapOf(
                "name" to name,
                "inviteCode" to inviteCode,
                FirestoreSchema.FIELD_MEMBER_USER_IDS to listOf(ownerUserId),
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
        householdSession.setHouseholdId(doc.id)
        return inviteCode
    }

    /** 배우자가 초대 코드를 입력해 기존 가구에 합류. */
    suspend fun joinHousehold(inviteCode: String, userId: String): Boolean {
        val query = firestore.collection(FirestoreSchema.HOUSEHOLDS)
            .whereEqualTo("inviteCode", inviteCode)
            .limit(1)
            .get()
            .await()
        val doc = query.documents.firstOrNull() ?: return false

        doc.reference.update(
            FirestoreSchema.FIELD_MEMBER_USER_IDS,
            FieldValue.arrayUnion(userId),
        ).await()

        householdSession.setHouseholdId(doc.id)
        return true
    }

    private fun generateInviteCode(): String =
        (1..6).map { Random.nextInt(0, 10) }.joinToString("")
}
