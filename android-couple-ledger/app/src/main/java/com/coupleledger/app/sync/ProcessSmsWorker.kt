package com.coupleledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coupleledger.app.sms.SmsTransactionRecorder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * SmsReceiver가 실시간으로 넘겨준 문자 한 통을 처리하는 1회성 워커.
 */
@HiltWorker
class ProcessSmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recorder: SmsTransactionRecorder,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis())

        return runCatching { recorder.tryRecord(sender, body, receivedAt) }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_RECEIVED_AT = "receivedAt"
    }
}
