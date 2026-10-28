# 5b단계 주간 회고 + 감정 추이 — 설계 문서

- 작성일: 2026-10-28
- 대상: `voice-diary-spec.md` §5.1 주간 회고, §7-7 리포트 화면
- 선행: 5a(위기 대응·검색) 완료
- 상태: 승인됨

## 1. 목적
월~일 한 주의 일기 요약을 Gemma에 넣어 한 문단 회고를 만들고, 같은 주의 날짜별 감정 추이를 보여준다.

## 2. 결정 사항
- 생성은 **수동 버튼**만. 백그라운드 자동 생성 없음(Gemma를 몰래 올리지 않음)
- 기간은 **월~일 주**. `week_start` = 월요일 `yyyy-MM-dd`. 같은 주 재생성 시 덮어씀(`id = "week-<weekStart>"`)
- 감정 추이: Compose Canvas 막대 7개(차트 라이브러리 추가 없음) + 감정별 횟수
- 위기 주간: 회고는 생성하되 위기 일기 요약은 입력에서 제외("힘든 날이 있었음"만), 화면 상단 `CrisisNotice`, 생성 본문이 `CrisisDetector`에 걸리면 본문 대신 고정 문구(표시 시점 검사, 스키마 변경 없음)
- 실행: 앱 범위 코루틴 스코프 + 공용 `ModelGate`(Mutex). WorkManager 재도입 없음. 녹음 파이프라인도 같은 게이트 사용 → Whisper·Gemma 동시 적재 방지

## 3. 데이터
- 스키마 v1 유지
- `EntryDao.between(start, end): Flow<List<EntryEntity>>` — `date >= :start AND date <= :end ORDER BY created_at`
- `WeeklyReportDao.byWeek(weekStart): Flow<WeeklyReportEntity?>`

## 4. 순수 로직 (`com.odom.privatediary.report`)
- `Week.start(date)`: 그 날짜 이전(포함)의 가장 가까운 월요일. `Week.days(start)`: 7일. `Week.end(start)` = start+6
- `WeeklyTrend.of(start, entries)`:
  - `days: List<DayPoint(date, emotion?, intensity?)>` 7개 — 그날 done 엔트리 중 강도 최대(동률이면 최신), 없으면 null
  - `counts: List<Pair<String, Int>>` — done 엔트리 감정별 횟수, 많은 순(동률은 `DiarySchema.EMOTIONS` 순)
- `WeeklyReportPrompt.SYSTEM_INSTRUCTION`: 한국어 한 문단 3~5문장, 조언·진단·상담 금지, 들은 내용 되짚기, 마크다운·목록 금지
- `WeeklyReportPrompt.build(entries): String?`:
  - done 엔트리: `"화 10/21 · 피곤(3) · <요약>"` 한 줄씩, 시간순
  - crisis 엔트리: `"화 10/21 · 힘든 날이 있었음"` (요약·감정 미포함)
  - failed/pending: 제외. 남는 줄이 없으면 null
- `ReportBody.clean(raw): String?`: 코드펜스·감싸는 따옴표 제거, trim, 700자 제한, 비면 null

## 5. 실행
- `bench/Runtimes.kt`: `interface TextGenerator { suspend fun load(); suspend fun generate(system: String, prompt: String): String; fun release() }`
- `bench/LiteRtTextGenerator.kt`: LiteRT-LM 구현 (CPU, temperature 0.6)
- `pipeline/ModelGate`: `suspend fun <T> withModel(onWait: () -> Unit = {}, block: suspend () -> T): T` — 잠겨 있으면 `onWait()` 후 대기. `ModelGate.shared` 싱글턴
- `report/WeeklyReportGenerator(entries, save, generator, gate, now)`:
  `suspend fun generate(weekStart: LocalDate): Outcome` — `NoEntries` | `Saved(report)`; 생성 실패·빈 본문은 예외. 게이트 안에서 load→generate→release(finally)
- `report/WeeklyReportService` (앱 범위 싱글턴): `state: StateFlow<ReportJob>` = `Idle | Waiting(week) | Running(week) | NoEntries(week) | Failed(week, message)`; `generate(week)`는 실행 중이면 무시
- `PipelineViewModel`: `pipeline.run`을 `ModelGate.shared.withModel { }` 안에서 실행

## 6. 화면 (`report` 경로)
- `ReportViewModel`: 선택 주 `StateFlow<LocalDate>`(이번 주 이후 불가), 그 주 entries·report Flow, 서비스 상태
- `ReportScreen`: ◀ `MM/dd ~ MM/dd` ▶ / 위기 엔트리 있으면 `CrisisNotice` / 추이 막대(Canvas, `EmotionPalette`, 높이=강도/5, 월~일 라벨) / 감정별 횟수 / 회고 본문(위기 검사) 또는 "아직 회고가 없어요" / [회고 만들기]·[다시 만들기], 진행·실패·기록 없음 표시 / 뒤로
- 홈에 [리포트] 버튼

## 7. 테스트 (JVM)
- `WeekTest`, `WeeklyTrendTest`, `WeeklyReportPromptTest`(위기 요약 제외, failed 제외, 빈 입력 null), `ReportBodyTest`
- `WeeklyReportGeneratorTest`(가짜 generator): 저장 id·weekStart·body, 기록 없음 시 generator 미적재, 생성 예외에도 release, 빈 본문 실패
- `ModelGateTest`: 두 작업이 겹치지 않음, 대기 시 onWait 호출
