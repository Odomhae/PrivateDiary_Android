# 5a단계 위기 대응 + 검색 Implementation Plan

> Inline execution (서브에이전트는 이 환경에서 Gradle 실행 불가). 순수 로직은 `./gradlew :app:testDebugUnitTest` TDD, 화면은 `assembleDebug` 컴파일 확인.

**Goal:** 규칙 기반 위기 감지 → 고정 문구 + 109 표시, 전사문·요약 `LIKE` 검색 화면.

**Architecture:** 순수 object(`CrisisDetector`, `SearchQuery`, `SearchSnippet`)에 로직을 모으고, 파이프라인·Repository가 호출한다. DB 스키마 v1 유지(DAO 쿼리만 추가/변경).

**Tech Stack:** Kotlin, Room 2.8.5, Compose, JUnit4, kotlinx-coroutines-test.

## Global Constraints
- 스펙: `docs/superpowers/specs/2026-10-27-phase5a-crisis-search-design.md`
- 감지 시 LLM `reply` 대신 고정 문구 + 자살예방 상담전화 **109**. 앱은 상담·진단하지 않음.
- 최종 crisis = 규칙 || LLM. 전사 수정 시 켜기만, 해제 없음.
- 다이얼은 `ACTION_DIAL` (권한 추가 금지). 네트워크 권한 추가 금지.
- 커밋 trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: CrisisDetector

**Files:** Create `app/src/main/java/com/odom/privatediary/safety/CrisisDetector.kt`, Test `app/src/test/java/com/odom/privatediary/safety/CrisisDetectorTest.kt`

**Produces:** `CrisisDetector.normalize(text: String): String`, `CrisisDetector.detect(text: String): Boolean`

- [ ] 테스트 작성: 띄어쓰기/문장부호 변형("죽고 싶다", "죽고싶어…", "자 살"), 키워드 각각 감지, 일상 문장("오늘 일 끝내고 싶다", "배고파 죽겠다", "여자 살 빠졌어", 빈 문자열) 미감지
- [ ] 실패 확인 → 구현:
```kotlin
object CrisisDetector {
    val PHRASES = listOf("죽고싶", "죽어버리고싶", "극단적선택", "목숨을끊", "목을매", "손목을긋",
        "뛰어내리고싶", "살기싫", "사라지고싶", "없어지고싶", "생을마감")
    val WORDS = listOf("자살", "자해", "유서")
    fun normalize(text: String) = text.filter { it.isLetterOrDigit() }
    fun detect(text: String): Boolean {
        val n = normalize(text)
        if (PHRASES.any { n.contains(it) }) return true
        return text.split(Regex("\s+")).map(::normalize).any { w -> WORDS.any { w.contains(it) } }
    }
}
```
- [ ] 통과 확인 → 커밋 `feat(safety): rule-based crisis keyword detector`

### Task 2: 파이프라인·Repository 연결

**Files:** Modify `pipeline/DiaryPipeline.kt`, `data/EntryDao.kt`, `data/DiaryRepository.kt`; Tests `DiaryPipelineTest.kt`, `DiaryRepositoryTest.kt`

**Consumes:** `CrisisDetector.detect`. **Produces:** `EntryDao.updateTranscript(id: String, transcript: String, crisis: Boolean)`, `DiaryRepository.updateTranscript(id, transcript)` (crisis 재계산)

- [ ] 테스트: 전사 "죽고 싶어" + LLM crisis=false → result.crisis=true; 일반 전사 → false. Repository: crisis=false 엔트리를 "자해" 포함으로 수정 → true; crisis=true 엔트리를 평범하게 수정 → true 유지; 없는 id → 아무것도 안 함
- [ ] 구현: `DiaryPipeline.run`에서 전사 후 `val ruleCrisis = CrisisDetector.detect(transcript.text)`, 분석 결과 `result.copy(crisis = ruleCrisis || result.crisis)`.
  DAO: `@Query("UPDATE entries SET transcript = :transcript, crisis = :crisis WHERE id = :id")`.
  Repository:
```kotlin
suspend fun updateTranscript(id: String, transcript: String) {
    val current = entryDao.byId(id) ?: return
    entryDao.updateTranscript(id, transcript, current.crisis || CrisisDetector.detect(transcript))
}
```
- [ ] 통과 → 커밋 `feat(safety): apply crisis rule in pipeline and on transcript edit`

### Task 3: 검색 로직 + DAO

**Files:** Create `data/SearchQuery.kt`, `ui/SearchSnippet.kt`; Modify `data/EntryDao.kt`, `data/DiaryRepository.kt`; Tests `data/SearchQueryTest.kt`, `ui/SearchSnippetTest.kt`, `DiaryRepositoryTest.kt`

**Produces:** `SearchQuery.toLikePattern(q: String): String?`, `SearchSnippet.around(text: String, q: String, radius: Int = 20): String`, `EntryDao.search(pattern: String): List<EntryEntity>`, `DiaryRepository.search(q: String): List<EntryEntity>`

- [ ] 테스트: `"  회사 "`→`"%회사%"`, `"100%"`→`"%100\%%"`, `"a_b"`→`"%a\_b%"`, `"\\"`→`"%\\\\%"`, `"  "`→null. Snippet: 짧은 텍스트 그대로, 중간 일치 시 양쪽 `…`, 앞부분 일치 시 앞 `…` 없음, 대소문자 무시, 미일치 시 앞 40자+`…`. Repository: 빈 쿼리 → DAO 미호출·빈 리스트, 일반 쿼리 → 패턴 전달
- [ ] 구현:
```kotlin
object SearchQuery {
    fun toLikePattern(q: String): String? {
        val t = q.trim().ifEmpty { return null }
        val escaped = t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }
}
// EntryDao
@Query("SELECT * FROM entries WHERE transcript LIKE :pattern ESCAPE '\\' OR summary LIKE :pattern ESCAPE '\\' ORDER BY created_at DESC")
suspend fun search(pattern: String): List<EntryEntity>
// DiaryRepository
suspend fun search(q: String): List<EntryEntity> = SearchQuery.toLikePattern(q)?.let { entryDao.search(it) } ?: emptyList()
```
  `SearchSnippet.around`: `idx = text.indexOf(q.trim(), ignoreCase = true)`; idx<0 → `text.take(2*radius)` (+`…` if longer); else `start = max(0, idx-radius)`, `end = min(len, idx+q.length+radius)`, prefix `…` if start>0, suffix `…` if end<len.
- [ ] 통과 → 커밋 `feat(search): LIKE search query, snippet, DAO + repository`

### Task 4: 화면

**Files:** Create `ui/CrisisNotice.kt`, `ui/SearchViewModel.kt`, `ui/SearchScreen.kt`; Modify `pipeline/PipelineScreen.kt`, `ui/DetailScreen.kt`, `ui/HomeScreen.kt`, `ui/AppNavHost.kt`, `download/OnboardingScreen.kt`

**Consumes:** `DiaryRepository.search`, `SearchSnippet.around`, `EntryEntity.crisis`, `AnalysisResult.crisis`

- [ ] `CrisisNotice()`: Card + 고정 문구 + `Button { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:109"))) }` "109 전화하기"
- [ ] PipelineScreen/DetailScreen: `if (crisis) CrisisNotice() else reply 표시`
- [ ] OnboardingScreen: 상단에 "이 앱은 상담이나 진단을 하지 않습니다. 위급할 때는 자살예방 상담전화 109로 연락하세요."
- [ ] SearchViewModel: `query: StateFlow<String>`, `onQuery(q)`, `results = query.debounce(300).mapLatest { repo.search(it) }.stateIn(...)`
- [ ] SearchScreen: `OutlinedTextField` + `LazyColumn` 카드(`date · emotion`, 스니펫: 전사문에 일치하면 전사문, 아니면 요약), 탭 → `onEntry(id)`, 뒤로 버튼; 쿼리 있고 결과 없으면 "결과 없음"
- [ ] Home에 `onSearch` 버튼, NavHost `search` 경로
- [ ] `./gradlew :app:testDebugUnitTest assembleDebug` 통과 → 커밋 `feat(ui): crisis notice, search screen, onboarding disclaimer`

## Self-Review
- 스펙 §3.1→T1, §3.2·3.4→T2, §3.3·3.5→T4, §4→T3·T4, §5→T1~T3. 누락 없음.
- 시그니처: `updateTranscript(id, transcript, crisis)`(DAO)·`updateTranscript(id, transcript)`(Repo) 일관.
