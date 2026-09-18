package com.coupleledger.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coupleledger.app.sms.SmsTransactionRecorder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * "정해진 시간마다 문자 읽어서 사용량 업데이트"의 본체.
 * 실시간 리시버(SmsReceiver)가 놓쳤을 수 있는 문자까지 포함해,
 * 지난 스캔 이후 도착한 문자함 전체를 다시 훑어 누락을 보완한다.
 *
 * READ_SMS 권한이 없으면(사용자가 거부) 조용히 스킵하고, 다음 주기에 다시 시도한다.
 */
@HiltWorker
class PeriodicSmsScanWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val recorder: SmsTransactionRecorder,
    private val syncPreferences: SyncPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success() // 권한 없으면 조용히 대기 (설정 화면에서 안내)
        }

        val since = syncPreferences.getLastSmsScanAt()
        val scanStartedAt = System.currentTimeMillis()
        var recorded = 0

        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val sender = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)

                val ok = runCatching { recorder.tryRecord(sender, body, date) }.getOrDefault(false)
                if (ok) recorded++
            }
        }

        syncPreferences.setLastSmsScanAt(scanStartedAt)
        return Result.success()
    }
}
