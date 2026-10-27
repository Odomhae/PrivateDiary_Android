package com.odom.privatediary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onEntry: (String) -> Unit,
    onBack: () -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQuery,
            label = { Text("일기 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // 결과를 만든 검색어(results.query) 기준으로 표시한다 — 입력 중인 query와 다를 수 있다.
        val searched = results.query
        if (searched.isNotBlank() && searched == query && results.entries.isEmpty()) Text("결과 없음")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results.entries, key = { it.id }) { e ->
                // 전사문에 일치하면 전사문, 아니면(요약에서 일치) 요약에서 스니펫을 만든다.
                val source = if (e.transcript.contains(searched.trim(), ignoreCase = true)) e.transcript
                else e.summary ?: e.transcript
                Card(Modifier.fillMaxWidth().clickable { onEntry(e.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${e.date} · ${e.emotion ?: "분석 전"}")
                        Text(SearchSnippet.around(source, searched))
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack) { Text("뒤로") }
    }
}
