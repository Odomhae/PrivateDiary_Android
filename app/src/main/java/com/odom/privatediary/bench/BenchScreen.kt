package com.odom.privatediary.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun BenchScreen(vm: BenchViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Phase 1 Bench")
        Text("whisper 모델: ${if (state.whisperReady) "준비됨" else "없음 (adb push 필요)"}")
        Text("gemma 모델: ${if (state.gemmaReady) "준비됨" else "없음 (adb push 필요)"}")
        Text(if (state.running != null) "실행 중: ${state.running}" else "대기")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.whisperReady && state.running == null,
                onClick = {
                    val wav = File(File(context.filesDir, "samples"), "sample01.wav")
                    vm.runWhisper(wav)
                },
            ) { Text("Whisper 실행") }

            Button(
                enabled = state.gemmaReady && state.running == null && state.transcript.isNotBlank(),
                onClick = { vm.runGemma() },
            ) { Text("Gemma 실행") }
        }

        if (state.transcript.isNotBlank()) Text("전사: ${state.transcript}")
        state.analysis?.let { Text("분석(ok=${it.parseOk}): ${it.raw}") }
        state.log.forEach { Text(it) }
    }
}
