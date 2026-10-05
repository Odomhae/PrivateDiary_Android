package com.odom.privatediary.download

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(vm: DownloadViewModel, onReady: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle(initialValue = 0)
    var wifiOnly by remember { mutableStateOf(true) }
    // POST_NOTIFICATIONS는 API 33+에서 런타임 요청. 결과와 무관하게 다운로드는 시작한다
    // (알림이 없어도 다운로드 자체는 진행되므로).
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.start(wifiOnly) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("음성 일기를 쓰려면 온디바이스 AI 모델을 먼저 받아야 합니다 (약 4GB).")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            Text("Wi-Fi에서만 다운로드")
        }
        Button(onClick = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("다운로드 시작")
        }
        LinearProgressIndicator(progress = { progress / 100f })
        Text("$progress%")
        if (progress >= 100) {
            Button(onClick = onReady) { Text("시작하기") }
        }
    }
}
