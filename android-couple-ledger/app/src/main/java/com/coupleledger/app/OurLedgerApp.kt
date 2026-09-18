package com.coupleledger.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.coupleledger.app.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OurLedgerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 앱 실행 시 "정해진 시간마다" 문자 스캔 + 동기화 주기 작업을 보장 등록.
        // 사용자가 설정 화면에서 주기를 바꾸면 SyncScheduler가 재등록한다.
        SyncScheduler.ensureScheduled(this)
    }
}
