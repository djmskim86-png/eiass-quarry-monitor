package com.coupleledger.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val intervalOptions = listOf(15L to "15분", 30L to "30분", 60L to "1시간")

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    var selected by remember { mutableLongStateOf(30L) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("문자 확인 · 부부 동기화 주기")
            intervalOptions.forEach { (minutes, label) ->
                Row {
                    RadioButton(
                        selected = selected == minutes,
                        onClick = {
                            selected = minutes
                            viewModel.updateSyncInterval(minutes)
                        },
                    )
                    Text(label)
                }
            }

            Button(onClick = { viewModel.syncNow() }) {
                Text("지금 바로 동기화")
            }

            Text(
                "※ 안드로이드 정책상 최소 주기는 15분입니다. " +
                    "카드 문자 자동 인식을 위해서는 '설정 > 앱 권한 > 문자 메시지' 허용이 필요합니다.",
            )
        }
    }
}
