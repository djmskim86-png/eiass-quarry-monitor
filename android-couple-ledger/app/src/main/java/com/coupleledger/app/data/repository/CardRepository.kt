package com.coupleledger.app.data.repository

import com.coupleledger.app.data.local.dao.CardDao
import com.coupleledger.app.data.local.entity.CardEntity
import com.coupleledger.app.data.remote.HouseholdSession
import com.coupleledger.app.data.remote.RemoteCardDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카드 목록(사용자가 이름을 붙인 카드들)을 관리.
 * 등록/이름 변경은 로컬에 즉시 반영하고, PeriodicSyncWorker가 다음 동기화 주기에
 * Firestore households/{id}/cards 로 올려 배우자 기기에도 전파한다.
 */
@Singleton
class CardRepository @Inject constructor(
    private val cardDao: CardDao,
    private val remoteCardDataSource: RemoteCardDataSource,
    private val householdSession: HouseholdSession,
) {
    fun observeCards(): Flow<List<CardEntity>> =
        householdSession.householdIdFlow.flatMapLatest { householdId ->
            if (householdId == null) flowOf(emptyList()) else cardDao.observeActiveCards(householdId)
        }

    suspend fun addCard(issuerCode: String, nickname: String, last4Digits: String?, monthlyBudget: Long?) {
        val householdId = householdSession.requireHouseholdId()
        val ownerUserId = householdSession.requireUserId()
        val card = CardEntity(
            id = java.util.UUID.randomUUID().toString(),
            householdId = householdId,
            ownerUserId = ownerUserId,
            issuerCode = issuerCode,
            nickname = nickname,
            last4Digits = last4Digits,
            monthlyBudget = monthlyBudget,
        )
        cardDao.upsert(card)
        remoteCardDataSource.push(householdId, card) // 배우자에게 즉시(다음 접속 시) 보이도록 바로 push
    }

    suspend fun renameCard(card: CardEntity, newNickname: String) {
        val updated = card.copy(nickname = newNickname, updatedAt = System.currentTimeMillis())
        cardDao.update(updated)
        remoteCardDataSource.push(updated.householdId, updated)
    }

    /** PeriodicSyncWorker가 호출: 배우자가 추가한 카드가 있으면 로컬에도 반영. */
    suspend fun pullRemoteCards() {
        val householdId = householdSession.householdIdOrNull() ?: return
        remoteCardDataSource.pullAll(householdId).forEach { cardDao.upsert(it) }
    }

    suspend fun removeCard(cardId: String) = cardDao.deactivate(cardId)

    /** 문자 파싱 결과(issuerCode)에 해당하는 등록된 카드를 찾는다. 없으면 자동 기록하지 않는다. */
    suspend fun findCardForIssuer(issuerCode: String): CardEntity? {
        val householdId = householdSession.householdIdOrNull() ?: return null
        return cardDao.findByIssuer(householdId, issuerCode)
    }
}
