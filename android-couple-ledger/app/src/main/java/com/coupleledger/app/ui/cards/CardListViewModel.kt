package com.coupleledger.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coupleledger.app.data.local.entity.CardEntity
import com.coupleledger.app.data.repository.CardRepository
import com.coupleledger.app.sms.parser.CardIssuerRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardListViewModel @Inject constructor(
    private val cardRepository: CardRepository,
) : ViewModel() {

    val cards: StateFlow<List<CardEntity>> = cardRepository.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 카드 추가 화면의 "카드사" 드롭다운에 쓰일 선택지 (국민카드, 신한카드 등). */
    val availableIssuers = CardIssuerRules.defaults

    fun addCard(issuerCode: String, nickname: String, last4Digits: String?, monthlyBudget: Long?) {
        viewModelScope.launch {
            cardRepository.addCard(issuerCode, nickname, last4Digits, monthlyBudget)
        }
    }

    fun renameCard(card: CardEntity, newNickname: String) {
        viewModelScope.launch { cardRepository.renameCard(card, newNickname) }
    }

    fun removeCard(card: CardEntity) {
        viewModelScope.launch { cardRepository.removeCard(card.id) }
    }
}
