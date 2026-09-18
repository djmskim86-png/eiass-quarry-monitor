package com.coupleledger.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coupleledger.app.data.local.entity.CardMonthlyTotal
import com.coupleledger.app.data.repository.CardRepository
import com.coupleledger.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val monthTotal: Long = 0,
    val byCard: List<CardMonthlyTotal> = emptyList(),
    val cardNickname: Map<String, String> = emptyMap(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    cardRepository: CardRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        transactionRepository.observeThisMonthTotal(),
        transactionRepository.observeThisMonthByCard(),
        cardRepository.observeCards(),
    ) { total, byCard, cards ->
        DashboardUiState(
            monthTotal = total,
            byCard = byCard,
            cardNickname = cards.associate { it.id to it.nickname },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
