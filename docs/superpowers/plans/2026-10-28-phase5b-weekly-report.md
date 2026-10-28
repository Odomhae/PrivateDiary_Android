# 5b단계 주간 회고 Implementation Plan

> Inline execution (서브에이전트는 이 환경에서 Gradle 실행 불가). 순수 로직은 `./gradlew :app:testDebugUnitTest` TDD, 화면·LiteRT는 `assembleDebug` 컴파일 확인.

**Goal:** 월~일 주 단위 Gemma 회고 문단 생성·저장 + 감정 추이 막대 화면.

**Architecture:** 순수 object(`Week`, `WeeklyTrend`, `WeeklyReportPrompt`, `ReportBody`) + 람다 주입형 `WeeklyReportGenerator`(JVM 테스트) + 앱 범위 `WeeklyReportService`. 모든 모델 사용은 `ModelGate` Mutex 안에서.

**Tech Stack:** Kotlin, Room 2.8.5, LiteRT-LM 0.17.1, Compose Canvas, JUnit4, kotlinx-coroutines-test.

## Global Constraints
- 스펙: `docs/superpowers/specs/2026-10-28-phase5b-weekly-report-design.md`
- Whisper·Gemma 동시 적재 금지 → 모델 사용은 `ModelGate.shared` 안에서만
- 긴 추론은 ViewModel 스코프 밖(앱 범위 서비스)
- 회고는 조언·진단·상담 금지. 위기 일기 요약은 LLM 입력 제외. 본문이 위기 규칙에 걸리면 고정 문구
- 네트워크·권한·차트 라이브러리 추가 금지. DB 스키마 v1 유지
- 커밋 trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

---

### Task 1: ModelGate + 파이프라인 적용
**Files:** Create `pipeline/ModelGate.kt`, Test `pipeline/ModelGateTest.kt`; Modify `pipeline/PipelineViewModel.kt`
**Produces:** `class ModelGate { suspend fun <T> withModel(onWait: () -> Unit = {}, block: suspend () -> T): T; companion { val shared } }`
- [ ] 테스트: 두 coroutine 동시 실행 시 구간이 겹치지 않음(진입/퇴장 로그 순서), 두 번째가 onWait 호출, 예외 시에도 잠금 해제
- [ ] 구현: `Mutex`; `if (!mutex.tryLock()) { onWait(); mutex.lock() }` → `try { block() } finally { mutex.unlock() }`
- [ ] PipelineViewModel: `withContext(dispatcher) { ModelGate.shared.withModel { pipeline.run(pcm, workWav) } }`
- [ ] 커밋 `feat(pipeline): ModelGate mutex so only one model job runs at a time`

### Task 2: 순수 로직
**Files:** Create `report/Week.kt`, `report/WeeklyTrend.kt`, `report/WeeklyReportPrompt.kt`, `report/ReportBody.kt` + 각 Test
**Produces:** `Week.start(LocalDate)`, `Week.days(LocalDate): List<LocalDate>`, `Week.end(LocalDate)`; `WeeklyTrend.of(start, entries): WeeklyTrend(days: List<DayPoint>, counts: List<Pair<String, Int>>)`; `WeeklyReportPrompt.SYSTEM_INSTRUCTION`, `WeeklyReportPrompt.build(entries): String?`; `ReportBody.clean(raw): String?`
- [ ] 테스트(스펙 §4 규칙 그대로) → 실패 확인 → 구현 → 통과
- [ ] 커밋 `feat(report): week math, trend, prompt, body cleanup`

### Task 3: DAO + TextGenerator + Generator + Service
**Files:** Modify `data/EntryDao.kt`, `data/WeeklyReportDao.kt`, `bench/Runtimes.kt`, `DiaryRepositoryTest.kt`(fake); Create `bench/LiteRtTextGenerator.kt`, `report/WeeklyReportGenerator.kt`, `report/WeeklyReportService.kt`, Test `report/WeeklyReportGeneratorTest.kt`
**Consumes:** Task 1·2. **Produces:** `EntryDao.between(start: String, end: String): Flow<List<EntryEntity>>`, `WeeklyReportDao.byWeek(weekStart: String): Flow<WeeklyReportEntity?>`, `WeeklyReportGenerator.generate(weekStart: LocalDate): Outcome`, `WeeklyReportService.get(context).state / generate(week)`
- [ ] Generator 테스트: Saved(id `week-2026-10-20`, body 정리됨), 기록 없음 → NoEntries + load 미호출, generate 예외 → 전파 + release 호출, 빈 본문 → 예외
- [ ] 구현 → 통과 → 커밋 `feat(report): weekly report generator, LiteRT text generator, app-scoped service`

### Task 4: 화면
**Files:** Create `report/ReportViewModel.kt`, `report/ReportScreen.kt`, `report/TrendBars.kt`; Modify `ui/HomeScreen.kt`, `ui/AppNavHost.kt`
- [ ] 스펙 §6대로 구현, `./gradlew :app:testDebugUnitTest assembleDebug` 통과
- [ ] 커밋 `feat(report): report screen with trend bars and weekly reflection`

## Self-Review
- 스펙 §3→T3, §4→T2, §5→T1·T3, §6→T4, §7→T1~T3. 누락 없음.
