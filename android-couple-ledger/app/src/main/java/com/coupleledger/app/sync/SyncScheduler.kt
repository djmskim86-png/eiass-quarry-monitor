package com.coupleledger.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 두 가지 주기 작업을 등록/재등록하는 창구.
 *
 * - SMS_SCAN_WORK: 문자함을 다시 훑어 누락된 결제 문자를 찾는다(네트워크 불필요).
 * - HOUSEHOLD_SYNC_WORK: 부부간 카드/거래를 Firestore로 주고받는다(네트워크 필요).
 *
 * Android는 PeriodicWorkRequest의 최소 주기를 15분으로 강제하므로,
 * 사용자가 그보다 짧은 값을 고르더라도 15분으로 보정한다.
 */
object SyncScheduler {
    const val SMS_SCAN_WORK = "sms_scan_work"
    const val HOUSEHOLD_SYNC_WORK = "household_sync_work"

    private const val MIN_INTERVAL_MINUTES = 15L
    private const val DEFAULT_INTERVAL_MINUTES = 30L

    fun ensureScheduled(context: Context, intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES) {
        scheduleSmsScan(context, intervalMinutes)
        scheduleHouseholdSync(context, intervalMinutes)
    }

    /** 설정 화면에서 사용자가 동기화 주기를 바꿀 때 호출. */
    fun updateInterval(context: Context, intervalMinutes: Long) {
        val clamped = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        scheduleSmsScan(context, clamped)
        scheduleHouseholdSync(context, clamped)
    }

    private fun scheduleSmsScan(context: Context, intervalMinutes: Long) {
        val clamped = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<PeriodicSmsScanWorker>(clamped, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SMS_SCAN_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun scheduleHouseholdSync(context: Context, intervalMinutes: Long) {
        val clamped = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(clamped, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(HOUSEHOLD_SYNC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** "지금 동기화" 버튼용: 주기와 별개로 즉시 1회 실행. */
    fun syncNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = androidx.work.OneTimeWorkRequestBuilder<PeriodicSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
