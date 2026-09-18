package com.coupleledger.app.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coupleledger.app.sms.parser.CardIssuerRule

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onDone: () -> Unit,
    viewModel: CardListViewModel = hiltViewModel(),
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedIssuer by remember { mutableStateOf<CardIssuerRule?>(null) }
    var nickname by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("카드사를 선택하면 그 카드사의 승인 문자를 자동으로 인식합니다.")

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedIssuer?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("카드사") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    viewModel.availableIssuers.forEach { issuer ->
                        DropdownMenuItem(
                            text = { Text(issuer.displayName) },
                            onClick = {
                                selectedIssuer = issuer
                                if (nickname.isBlank()) nickname = issuer.displayName
                                expanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("내가 부를 이름 (예: 국민카드-생활비)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = last4,
                onValueChange = { if (it.length <= 4) last4 = it.filter(Char::isDigit) },
                label = { Text("카드 끝 4자리 (선택)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val issuer = selectedIssuer ?: return@Button
                    viewModel.addCard(
                        issuerCode = issuer.code,
                        nickname = nickname.ifBlank { issuer.displayName },
                        last4Digits = last4.ifBlank { null },
                        monthlyBudget = null,
                    )
                    onDone()
                },
                enabled = selectedIssuer != null && nickname.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("카드 추가")
            }
        }
    }
}
