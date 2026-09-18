package com.coupleledger.app.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * "카드선택(국민카드 등 내가 이름 추가)" 요구사항의 목록 화면.
 * 카드사(issuer)를 고르고 별칭(nickname)을 붙이면, 그 카드사에서 오는 승인 문자가
 * 자동으로 이 카드의 거래로 기록된다.
 */
@Composable
fun CardListScreen(
    onAddCardClick: () -> Unit,
    viewModel: CardListViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCardClick) {
                Icon(Icons.Default.Add, contentDescription = "카드 추가")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cards) { card ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(card.nickname, style = MaterialTheme.typography.titleMedium)
                            Text(
                                card.last4Digits?.let { "끝자리 $it" } ?: "카드번호 미등록",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
