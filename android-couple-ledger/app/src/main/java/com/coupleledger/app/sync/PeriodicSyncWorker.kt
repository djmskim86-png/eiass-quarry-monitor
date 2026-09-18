package com.coupleledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coupleledger.app.data.repository.CardRepository
import com.coupleledger.app.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * "정해진 시간마다 서로 공유 및 동기화"의 본체.
 * 1) 내가 새로 추가한 카드/거래를 Firestore로 올리고(push)
 * 2) 배우자가 올린 새 카드/거래를 받아온다(pull).
 *
 * 앱이 꺼져 있어도 OS가 이 주기를 깨워 실행해준다(WorkManager PeriodicWorkRequest).
 * 네트워크가 없으면 WorkManager 제약조건(NetworkType.CONNECTED)에 의해
 * 자동으로 연결될 때까지 대기했다가 실행된다.
 */
@HiltWorker
class PeriodicSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val cardRepository: CardRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        cardRepository.pullRemoteCards()
        transactionRepository.pushPending()
        transactionRepository.pullRemote()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
