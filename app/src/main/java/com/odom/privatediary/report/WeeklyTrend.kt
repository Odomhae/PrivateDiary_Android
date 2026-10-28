package com.odom.privatediary.report

import com.odom.privatediary.bench.DiarySchema
import com.odom.privatediary.data.EntryEntity
import java.time.LocalDate

data class DayPoint(val date: LocalDate, val emotion: String?, val intensity: Int?)

/** 한 주의 날짜별 대표 감정과 감정별 횟수. 분석 완료(done) 엔트리만 쓴다. */
data class WeeklyTrend(val days: List<DayPoint>, val counts: List<Pair<String, Int>>) {
    companion object {
        fun of(start: LocalDate, entries: List<EntryEntity>): WeeklyTrend {
            val done = entries.filter { it.analysisStatus == "done" && it.emotion != null }
            val byDate = done.groupBy { it.date }
            val days = Week.days(start).map { d ->
                // 강도 최대, 동률이면 최신
                val pick = byDate[d.toString()]
                    ?.maxWithOrNull(compareBy<EntryEntity>({ it.intensity ?: 0 }, { it.createdAt }))
                DayPoint(d, pick?.emotion, pick?.intensity)
            }
            val counts = done.groupingBy { it.emotion!! }.eachCount().toList()
                .sortedWith(compareBy({ -it.second }, { DiarySchema.EMOTIONS.indexOf(it.first) }))
            return WeeklyTrend(days, counts)
        }
    }
}
