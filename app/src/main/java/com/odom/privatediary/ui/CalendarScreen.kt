package com.odom.privatediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CalendarScreen(
    vm: CalendarViewModel,
    onDayEntry: (String) -> Unit,
    onBack: () -> Unit,
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val byDate = EntryGrouping.byDate(entries)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.prevMonth() }) { Text("<") }
            Text("${month.year}.${month.monthValue}")
            TextButton(onClick = { vm.nextMonth() }) { Text(">") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { Text(it) }
        }
        val cells = CalendarMonth.cells(month.year, month.monthValue)
        LazyVerticalGrid(columns = GridCells.Fixed(7)) {
            items(cells) { cell ->
                val date = cell.day?.let { "%04d-%02d-%02d".format(month.year, month.monthValue, it) }
                val dayEntries = date?.let { byDate[it] }.orEmpty()
                val color = Color(EmotionPalette.argb(EntryGrouping.dominantEmotion(dayEntries)))
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .padding(2.dp)
                        .background(if (dayEntries.isEmpty()) Color.Transparent else color)
                        .clickable(enabled = dayEntries.isNotEmpty()) {
                            dayEntries.maxByOrNull { it.createdAt }?.let { onDayEntry(it.id) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    cell.day?.let { Text(it.toString()) }
                }
            }
        }
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}
