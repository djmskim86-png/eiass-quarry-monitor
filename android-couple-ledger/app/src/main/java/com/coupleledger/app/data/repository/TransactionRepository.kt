package com.coupleledger.app.data.repository

import com.coupleledger.app.data.local.dao.TransactionDao
import com.coupleledger.app.data.local.entity.CardMonthlyTotal
import com.coupleledger.app.data.local.entity.MemberMonthlyTotal
import com.coupleledger.app.data.local.entity.SyncStatus
import com.coupleledger.app.data.local.entity.TransactionEntity
import com.coupleledger.app.data.remote.HouseholdSession
import com.coupleledger.app.data.remote.RemoteTransactionDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val remoteDataSource: RemoteTransactionDataSource,
    private val householdSession: HouseholdSession,
) {
    fun observeAll(): Flow<List<TransactionEntity>> =
        householdSession.householdIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else transactionDao.observeAll(id)
        }

    /** 이번 달 부부 합산 지출 합계 (대시보드 상단 숫자). */
    fun observeThisMonthTotal(): Flow<Long> =
        householdSession.householdIdFlow.flatMapLatest { id ->
            if (id == null) return@flatMapLatest flowOf(0L)
            val (start, end) = currentMonthRange()
            transactionDao.observeMonthlyTotal(id, start, end)
        }

    fun observeThisMonthByCard(): Flow<List<CardMonthlyTotal>> =
        householdSession.householdIdFlow.flatMapLatest { id ->
            if (id == null) return@flatMapLatest flowOf(emptyList())
            val (start, end) = currentMonthRange()
            transactionDao.observeMonthlyTotalByCard(id, start, end)
        }

    fun observeThisMonthByMember(): Flow<List<MemberMonthlyTotal>> =
        householdSession.householdIdFlow.flatMapLatest { id ->
            if (id == null) return@flatMapLatest flowOf(emptyList())
            val (start, end) = currentMonthRange()
            transactionDao.observeMonthlyTotalByMember(id, start, end)
        }

    suspend fun insertParsed(transaction: TransactionEntity) {
        transactionDao.insert(transaction)
    }

    /** PeriodicSyncWorker가 호출: 로컬에서 아직 안 올라간 것들을 Firestore로 push. */
    suspend fun pushPending() {
        val householdId = householdSession.householdIdOrNull() ?: return
        val pending = transactionDao.getPendingSync()
        if (pending.isEmpty()) return
        remoteDataSource.push(householdId, pending)
        pending.forEach { transactionDao.update(it.copy(syncStatus = SyncStatus.SYNCED)) }
    }

    /** PeriodicSyncWorker가 호출: 배우자 기기가 올린 새 내역을 받아와 로컬에 병합. */
    suspend fun pullRemote() {
        val householdId = householdSession.householdIdOrNull() ?: return
        val since = transactionDao.getLatestOccurredAt(householdId) ?: 0L
        val remoteNew = remoteDataSource.pullSince(householdId, since)
        if (remoteNew.isNotEmpty()) transactionDao.insertAll(remoteNew)
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
