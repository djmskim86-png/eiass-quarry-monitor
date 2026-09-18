package com.coupleledger.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_HOUSEHOLD_ID = stringPreferencesKey("household_id")

/**
 * "지금 로그인한 사용자가 어느 가구(householdId)에 속해 있는가"를 들고 있는 얇은 세션 객체.
 * 온보딩에서 가구를 새로 만들거나 배우자의 초대 코드로 참여하면 여기에 저장된다.
 */
@Singleton
class HouseholdSession @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val firebaseAuth: FirebaseAuth,
) {
    val householdIdFlow: Flow<String?> = dataStore.data.map { it[KEY_HOUSEHOLD_ID] }

    suspend fun setHouseholdId(householdId: String) {
        dataStore.edit { it[KEY_HOUSEHOLD_ID] = householdId }
    }

    suspend fun householdIdOrNull(): String? = householdIdFlow.first()

    suspend fun requireHouseholdId(): String =
        householdIdOrNull() ?: error("아직 가구(householdId)가 설정되지 않았습니다. 온보딩을 먼저 완료하세요.")

    fun requireUserId(): String =
        firebaseAuth.currentUser?.uid ?: error("로그인이 필요합니다.")
}
