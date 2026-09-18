package com.coupleledger.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coupleledger.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    /** 사용자가 15/30/60분 중 고르면 두 주기 워커(문자 스캔, 부부 동기화) 모두 재등록. */
    fun updateSyncInterval(minutes: Long) {
        viewModelScope.launch {
            SyncScheduler.updateInterval(getApplication(), minutes)
        }
    }

    fun syncNow() {
        SyncScheduler.syncNow(getApplication())
    }
}
