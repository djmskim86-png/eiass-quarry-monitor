package com.coupleledger.app.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_LAST_SMS_SCAN_AT = longPreferencesKey("last_sms_scan_at")

/**
 * "정해진 시간마다 문자 읽어서 업데이트"의 기준점을 들고 있는 저장소.
 * 마지막으로 스캔을 끝낸 시각(epoch millis)을 저장해두면, 다음 주기 워커는
 * 그 이후 도착한 문자함만 조회해 배터리/시간을 아낄 수 있다.
 */
@Singleton
class SyncPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getLastSmsScanAt(): Long = dataStore.data.first()[KEY_LAST_SMS_SCAN_AT] ?: 0L

    suspend fun setLastSmsScanAt(timestamp: Long) {
        dataStore.edit { it[KEY_LAST_SMS_SCAN_AT] = timestamp }
    }
}
