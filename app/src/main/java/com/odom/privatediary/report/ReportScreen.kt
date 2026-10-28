package com.odom.privatediary.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odom.privatediary.safety.CrisisDetector
import com.odom.privatediary.ui.CRISIS_MESSAGE
import com.odom.privatediary.ui.CrisisNotice
import java.time.format.DateTimeFormatter

private val MD = DateTimeFormatter.ofPattern("MM/dd")

@Composable
fun ReportScreen(vm: ReportViewModel, onBack: () -> Unit) {
    val week by vm.week.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val trend = remember(week, entries) { WeeklyTrend.of(week, entries) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::previousWeek) { Text("◀") }
            Text("${week.format(MD)} ~ ${Week.end(week).format(MD)}")
            OutlinedButton(onClick = vm::nextWeek, enabled = week < vm.thisWeek) { Text("▶") }
        }

        if (entries.any { it.crisis }) CrisisNotice()

        Text("감정 추이")
        TrendBars(trend.days)
        if (trend.counts.isNotEmpty()) {
            Text(trend.counts.joinToString(" · ") { (emotion, n) -> "$emotion $n" })
        }

        Text("주간 회고")
        val body = report?.body
        when {
            body == null -> Text("아직 회고가 없어요.")
            // 생성 본문도 위기 규칙으로 검사 — 걸리면 본문 대신 고정 문구
            CrisisDetector.detect(body) -> Text(CRISIS_MESSAGE)
            else -> Text(body)
        }

        // 서비스 상태는 앱 전역이라, 지금 보고 있는 주에 대한 것만 표시한다.
        when (val j = job) {
            is ReportJob.Waiting -> if (j.week == week) {
                LinearProgressIndicator()
                Text("다른 분석이 끝나길 기다리는 중…")
            }
            is ReportJob.Running -> if (j.week == week) {
                LinearProgressIndicator()
                Text("회고를 쓰는 중… (수십 초 걸릴 수 있어요)")
            }
            is ReportJob.NoEntries -> if (j.week == week) Text("이 주에는 분석된 일기가 없어요.")
            is ReportJob.Failed -> if (j.week == week) Text(j.message)
            ReportJob.Idle -> Unit
        }
        // 생성은 한 번에 하나(서비스가 중복 요청을 무시) — 다른 주 생성 중에도 버튼을 막는다.
        val generating = job is ReportJob.Running || job is ReportJob.Waiting
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::generate, enabled = !generating) {
                Text(if (report == null) "회고 만들기" else "다시 만들기")
            }
            OutlinedButton(onClick = onBack) { Text("뒤로") }
        }
    }
}
