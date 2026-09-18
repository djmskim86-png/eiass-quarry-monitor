package com.coupleledger.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coupleledger.app.data.repository.HouseholdRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState
    data object Loading : OnboardingUiState
    data class Created(val inviteCode: String) : OnboardingUiState
    data object Joined : OnboardingUiState
    data class Error(val message: String) : OnboardingUiState
}

/**
 * 첫 실행 시 "새 가계부 만들기(초대코드 발급)" 또는 "배우자 초대코드로 참여" 중 하나를 선택.
 * Firebase Auth는 이메일/비번 없이 익명 로그인으로 시작해도 되고,
 * 추후 Google 로그인으로 업그레이드할 수 있다(부부 각자 기기에서 로그인).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val state: StateFlow<OnboardingUiState> = _state

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _state.value = OnboardingUiState.Loading
            runCatching {
                val uid = ensureSignedIn()
                householdRepository.createHousehold(name, uid)
            }.onSuccess { code ->
                _state.value = OnboardingUiState.Created(code)
            }.onFailure {
                _state.value = OnboardingUiState.Error(it.message ?: "가계부 생성 실패")
            }
        }
    }

    fun joinHousehold(inviteCode: String) {
        viewModelScope.launch {
            _state.value = OnboardingUiState.Loading
            runCatching {
                val uid = ensureSignedIn()
                householdRepository.joinHousehold(inviteCode, uid)
            }.onSuccess { found ->
                _state.value = if (found) OnboardingUiState.Joined
                else OnboardingUiState.Error("초대 코드를 찾을 수 없습니다")
            }.onFailure {
                _state.value = OnboardingUiState.Error(it.message ?: "참여 실패")
            }
        }
    }

    private suspend fun ensureSignedIn(): String {
        firebaseAuth.currentUser?.let { return it.uid }
        val result = firebaseAuth.signInAnonymously().await()
        return result.user?.uid ?: error("로그인에 실패했습니다")
    }
}
