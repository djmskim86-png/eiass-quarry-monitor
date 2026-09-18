package com.coupleledger.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 기기 재부팅 후 WorkManager의 주기 작업이 사라지지 않도록 재등록한다. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncScheduler.ensureScheduled(context)
        }
    }
}
