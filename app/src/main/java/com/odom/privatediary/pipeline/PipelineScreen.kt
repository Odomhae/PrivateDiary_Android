package com.odom.privatediary.pipeline

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odom.privatediary.ui.CrisisNotice

@Composable
fun PipelineScreen(vm: PipelineViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.start() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("음성 일기")
        Text("모델: ${if (vm.modelsReady) "준비됨" else "없음 (adb push 필요)"}")
        Text("단계: ${state.stage}")
        Button(
            enabled = vm.modelsReady && (
                state.stage == PipelineStage.Idle ||
                    state.stage == PipelineStage.Done ||
                    state.stage == PipelineStage.Failed
                ),
            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        ) { Text("녹음 시작") }
        Button(
            enabled = state.stage == PipelineStage.Recording,
            onClick = { vm.stopRecording() },
        ) { Text("정지") }

        state.analysis?.let {
            Text("요약: ${it.summary}")
            Text("감정: ${it.emotion} (강도 ${it.intensity})")
            Text("태그: ${it.keywords.joinToString(", ")}")
            if (it.crisis) CrisisNotice() else Text("공감: ${it.reply}")
        }
        state.error?.let { Text("오류: $it") }
    }
}
