package com.odom.privatediary.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(vm: DownloadViewModel, onReady: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Play 확인 대화상자(모바일 데이터 사용 등). 결과와 무관하게 상태를 다시 조회한다.
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("이 앱은 상담이나 진단을 하지 않습니다. 위급할 때는 자살예방 상담전화 109로 연락하세요.")
        Text("음성 일기를 쓰려면 온디바이스 AI 모델이 필요합니다 (약 1.1GB). Play 스토어가 설치 직후 자동으로 받아 둡니다.")
        when (val s = state) {
            PackUiState.Checking -> Text("모델 상태 확인 중…")
            PackUiState.NotStarted -> Button(onClick = vm::fetch) { Text("다운로드 시작") }
            is PackUiState.Downloading -> {
                LinearProgressIndicator(progress = { s.percent / 100f })
                Text("${s.percent}%")
            }
            PackUiState.WaitingForWifi -> {
                Text("Wi-Fi 연결을 기다리는 중입니다.")
                Button(onClick = { vm.confirm(confirmLauncher) }) { Text("모바일 데이터로 받기") }
            }
            PackUiState.NeedsConfirmation ->
                Button(onClick = { vm.confirm(confirmLauncher) }) { Text("다운로드 허용") }
            is PackUiState.Failed -> {
                Text(s.message)
                Button(onClick = vm::fetch) { Text("다시 시도") }
            }
            PackUiState.Ready -> Button(onClick = onReady) { Text("시작하기") }
        }
    }
}
