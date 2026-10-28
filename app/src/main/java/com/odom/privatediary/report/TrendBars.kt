package com.odom.privatediary.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.odom.privatediary.ui.EmotionPalette

/** 월~일 막대 7개: 색 = 대표 감정, 높이 = 강도/5. 기록 없는 날은 바닥선만. */
@Composable
fun TrendBars(days: List<DayPoint>, modifier: Modifier = Modifier) {
    val maxIntensity = 5f // DiarySchema.clampIntensity 범위 1..5
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / days.size
            val barWidth = slot * 0.6f
            days.forEachIndexed { i, d ->
                val left = i * slot + (slot - barWidth) / 2
                if (d.emotion != null) {
                    // 강도가 없으면 중간 높이(3)로 그려 횟수 집계와 어긋나지 않게 한다.
                    val intensity = d.intensity ?: 3
                    val h = size.height * (intensity / maxIntensity).coerceIn(0.1f, 1f)
                    drawRoundRect(
                        color = Color(EmotionPalette.argb(d.emotion)),
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                } else {
                    drawRect(
                        color = Color(EmotionPalette.NEUTRAL).copy(alpha = 0.4f),
                        topLeft = Offset(left, size.height - 4f),
                        size = Size(barWidth, 4f),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(Week.dayLabel(d.date), Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
}
