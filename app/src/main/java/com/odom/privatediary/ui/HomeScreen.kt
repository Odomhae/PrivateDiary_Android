package com.odom.privatediary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onRecord: () -> Unit,
    onCalendar: () -> Unit,
    onSearch: () -> Unit,
    onReport: () -> Unit,
    onEntry: (String) -> Unit,
) {
    val entries by vm.entries.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("음성 일기")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRecord) { Text("녹음") }
            OutlinedButton(onClick = onCalendar) { Text("캘린더") }
            OutlinedButton(onClick = onSearch) { Text("검색") }
            OutlinedButton(onClick = onReport) { Text("리포트") }
        }
        Text("최근 일기")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { e ->
                Card(
                    Modifier.fillMaxWidth().clickable { onEntry(e.id) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${e.date} · ${e.emotion ?: "분석 전"}")
                        Text(e.summary ?: e.transcript.take(30))
                    }
                }
            }
        }
    }
}
