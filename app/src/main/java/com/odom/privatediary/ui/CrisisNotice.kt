package com.odom.privatediary.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/** 위기 표현 감지 시 LLM reply 대신 보여주는 고정 안내 (스펙 §5.3). 상담·진단을 하지 않는다. */
@Composable
fun CrisisNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(CRISIS_MESSAGE)
            // ACTION_DIAL은 다이얼러만 열어 CALL_PHONE 권한이 필요 없다.
            // 다이얼러가 없는 기기(Wi-Fi 태블릿 등)에서도 죽지 않게 한다 — 번호는 문구에 보인다.
            Button(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$CRISIS_LINE".toUri())) }
            }) { Text("$CRISIS_LINE 전화하기") }
        }
    }
}

const val CRISIS_LINE = "109"
const val CRISIS_MESSAGE =
    "지금 많이 힘드신 것 같아요. 혼자 견디지 않으셔도 됩니다. " +
        "자살예방 상담전화 109(24시간)에서 이야기를 들어드려요."
