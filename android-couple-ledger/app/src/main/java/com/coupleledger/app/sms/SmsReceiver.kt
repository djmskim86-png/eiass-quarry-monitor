package com.coupleledger.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.coupleledger.app.sync.ProcessSmsWorker

/**
 * 문자가 "도착하는 순간" 실시간으로 잡아내는 리시버.
 * 무거운 작업(파싱/DB/네트워크)은 여기서 직접 하지 않고 WorkManager에 위임한다
 * (리시버는 실행 시간이 매우 짧게 제한되기 때문).
 *
 * 주기적 스캔(PeriodicSmsScanWorker)은 이 리시버가 놓친 문자
 * (예: 앱이 강제 종료됐거나 리시버 등록 전에 온 문자)를 뒤늦게 보완하는 역할을 한다.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val now = System.currentTimeMillis()

        for (message in messages) {
            val sender = message.originatingAddress ?: message.displayOriginatingAddress ?: continue
            val body = message.messageBody ?: continue

            val data = androidx.work.Data.Builder()
                .putString(ProcessSmsWorker.KEY_SENDER, sender)
                .putString(ProcessSmsWorker.KEY_BODY, body)
                .putLong(ProcessSmsWorker.KEY_RECEIVED_AT, now)
                .build()

            val request = OneTimeWorkRequestBuilder<ProcessSmsWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
