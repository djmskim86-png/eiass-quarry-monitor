package com.coupleledger.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coupleledger.app.data.local.entity.TransactionEntity
import com.coupleledger.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {
    val transactions: StateFlow<List<TransactionEntity>> = transactionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
