package com.coupleledger.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingScreen(
    onReady: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var householdName by remember { mutableStateOf("우리집") }
    var inviteCodeInput by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is OnboardingUiState.Joined) onReady()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("부부가 함께 쓰는 가계부를 시작해보세요")

            Text("1) 새로 만들기")
            OutlinedTextField(
                value = householdName,
                onValueChange = { householdName = it },
                label = { Text("가계부 이름") },
            )
            Button(onClick = { viewModel.createHousehold(householdName) }) {
                Text("새 가계부 만들기")
            }

            when (val s = state) {
                is OnboardingUiState.Created -> {
                    Text("초대 코드: ${s.inviteCode}\n배우자에게 이 코드를 공유하세요.")
                    Button(onClick = onReady) { Text("계속하기") }
                }
                is OnboardingUiState.Error -> Text("오류: ${s.message}")
                else -> Unit
            }

            Text("2) 배우자 초대코드로 참여")
            OutlinedTextField(
                value = inviteCodeInput,
                onValueChange = { inviteCodeInput = it },
                label = { Text("초대 코드 6자리") },
            )
            Button(onClick = { viewModel.joinHousehold(inviteCodeInput) }) {
                Text("참여하기")
            }
        }
    }
}
