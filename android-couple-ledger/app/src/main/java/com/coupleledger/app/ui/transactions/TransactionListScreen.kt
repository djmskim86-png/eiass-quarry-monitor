package com.coupleledger.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionListScreen(viewModel: TransactionListViewModel = hiltViewModel()) {
    val transactions by viewModel.transactions.collectAsState()
    val krw = NumberFormat.getNumberInstance(Locale.KOREA)
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(transactions) { tx ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(tx.merchant ?: "가맹점 미상", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${krw.format(tx.amount)}원 · ${dateFormat.format(Date(tx.occurredAt))}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
