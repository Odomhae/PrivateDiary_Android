package com.odom.privatediary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DetailScreen(
    entryId: String,
    vm: DetailViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(entryId) { vm.load(entryId) }
    val entry by vm.entry.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val e = entry
        if (e == null) {
            Text("불러오는 중…")
        } else {
            Text("${e.date} · ${e.emotion ?: "분석 전"} ${e.intensity?.let { "(강도 $it)" } ?: ""}")
            e.summary?.let { Text("요약: $it") }
            if (e.keywords.isNotEmpty()) Text("태그: ${e.keywords.joinToString(", ")}")
            if (e.crisis) CrisisNotice() else e.reply?.let { Text("공감: $it") }

            var text by remember(e.id) { mutableStateOf(e.transcript) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("전사문") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveTranscript(e.id, text) }) { Text("저장") }
                OutlinedButton(onClick = { vm.delete(e.id, onBack) }) { Text("삭제") }
                OutlinedButton(onClick = onBack) { Text("뒤로") }
            }
        }
    }
}
