# 3단계-b 화면 Implementation Plan

> Inline execution. Pure logic TDD via `./gradlew :app:testDebugUnitTest`; Compose/NavHost/VM via `compileDebugKotlin`. Branch `feat/phase3-screens`.

**Goal:** `DiaryRepository` 데이터를 홈·캘린더·상세 화면과 Navigation Compose로 보여준다(재분석 제외).

**Tech Stack:** Compose, Navigation Compose, Coroutines, java.time, Room(기존 repo).

## Global Constraints
- 패키지 `com.odom.privatediary.ui`. 수동 DI(`DiaryDatabase.get(app)`).
- 감정 색은 `EmotionPalette.argb(String?)` 고정 매핑(7+중립); Compose가 `Color(argb)`로 변환.
- 캘린더는 직접 월그리드(일요일 시작, java.time), 라이브러리 없음.
- 재분석/잠금/검색/설정 제외.
- 커밋 trailer: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

### Task 1: 빌드 — navigation-compose
- libs: `navigationCompose = "2.9.5"`(해석 실패 시 교정); `androidx-navigation-compose = { group="androidx.navigation", name="navigation-compose", version.ref="navigationCompose" }`.
- build.gradle: `implementation(libs.androidx.navigation.compose)`.
- Commit: `build: add navigation-compose`.

### Task 2: EmotionPalette (순수/TDD)
`ui/EmotionPalette.kt` + test. `argb(emotion)` 7 감정 distinct, null/unknown→NEUTRAL.
```kotlin
package com.odom.privatediary.ui

object EmotionPalette {
    const val NEUTRAL = 0xFFB0BEC5
    private val map = mapOf(
        "기쁨" to 0xFFFFD54F, "평온" to 0xFF81C784, "설렘" to 0xFFFF8A65,
        "슬픔" to 0xFF64B5F6, "불안" to 0xFF9575CD, "분노" to 0xFFE57373, "피곤" to 0xFF90A4AE,
    )
    fun argb(emotion: String?): Long = map[emotion] ?: NEUTRAL
}
```

### Task 3: CalendarMonth (순수/TDD, java.time)
`ui/CalendarMonth.kt` + test.
```kotlin
package com.odom.privatediary.ui

import java.time.YearMonth

data class CalendarCell(val day: Int?)

object CalendarMonth {
    /** 일요일 시작 그리드. 앞 공백(null) + 1..말일. */
    fun cells(year: Int, month: Int): List<CalendarCell> {
        val ym = YearMonth.of(year, month)
        val lead = ym.atDay(1).dayOfWeek.value % 7  // SUN=7%7=0, MON=1..SAT=6
        val blanks = List(lead) { CalendarCell(null) }
        val days = (1..ym.lengthOfMonth()).map { CalendarCell(it) }
        return blanks + days
    }
}
```
테스트: 2026-02 → 28셀, cells[0].day==1 (2026-02-01 일요일); 2026-01 → 35셀, cells[0..3].day==null, cells[4].day==1.

### Task 4: EntryGrouping (순수/TDD)
`ui/EntryGrouping.kt` + test.
```kotlin
package com.odom.privatediary.ui

import com.odom.privatediary.data.EntryEntity

object EntryGrouping {
    fun byDate(entries: List<EntryEntity>): Map<String, List<EntryEntity>> = entries.groupBy { it.date }
    /** 그날 대표 감정 = 가장 최근 createdAt 엔트리의 emotion. */
    fun dominantEmotion(entries: List<EntryEntity>): String? = entries.maxByOrNull { it.createdAt }?.emotion
}
```
테스트: byDate 그룹 개수/키; dominantEmotion 최신 createdAt의 감정.

### Task 5: ViewModels (스캐폴드)
`ui/HomeViewModel.kt`, `ui/CalendarViewModel.kt`, `ui/DetailViewModel.kt` — 각자 `DiaryRepository(DiaryDatabase.get(app).entryDao())` 구성.
- Home: `entries: StateFlow<List<EntryEntity>>` = repo.observeEntries() stateIn.
- Calendar: `entries` 동일 + 표시 월 상태(YearMonth) 이동.
- Detail: `load(id)`, `transcript` 편집 상태, `save()`(updateTranscript), `delete()`.

### Task 6: Screens + NavHost + MainActivity (스캐폴드)
`ui/HomeScreen.kt`, `ui/CalendarScreen.kt`, `ui/DetailScreen.kt`, `ui/AppNavHost.kt`; `MainActivity` → `AppNavHost`. `record` 라우트는 기존 `pipeline.PipelineScreen`.

전체 검증: `./gradlew :app:testDebugUnitTest`(순수 테스트 + 전체 컴파일). 성공 시 커밋.

## Self-Review
- §7 화면 home/result(record)/calendar/detail → T5/T6; 색 T2; 달력 T3; 그룹핑 T4; nav T6; 빌드 T1.
- 재분석/잠금/검색/설정 제외 확인. 순수 로직만 JVM 테스트; 화면은 컴파일 검증 + 기기 시각.
