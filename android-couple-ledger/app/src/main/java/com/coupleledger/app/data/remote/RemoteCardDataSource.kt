package com.coupleledger.app.data.remote

import com.coupleledger.app.data.local.entity.CardEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteCardDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun collection(householdId: String) = firestore
        .collection(FirestoreSchema.HOUSEHOLDS)
        .document(householdId)
        .collection(FirestoreSchema.CARDS)

    suspend fun push(householdId: String, card: CardEntity) {
        collection(householdId).document(card.id).set(card.toRemoteMap()).await()
    }

    suspend fun pullAll(householdId: String): List<CardEntity> =
        collection(householdId).get().await().documents.mapNotNull { it.toCardEntityOrNull(householdId) }
}
