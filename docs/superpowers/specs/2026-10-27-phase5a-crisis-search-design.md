# 5a단계 위기 대응 + 검색 — 설계 문서

- 작성일: 2026-10-27
- 대상: `voice-diary-spec.md` §8 5단계 중 위기 대응(§5.3)과 원문 검색(§5.1)
- 선행: 1~4단계 완료 (4단계는 Play AI pack으로 전환됨)
- 후속: 5b 주간 회고, 5c 앱 잠금
- 상태: 승인됨

## 1. 목적
- 전사문에서 자해·극단적 선택 암시 표현을 **규칙 기반으로 먼저** 감지하고, 감지 시 LLM `reply` 대신 고정 문구와 자살예방 상담전화 109를 보여준다. 앱은 상담·진단을 하지 않는다.
- 전사문·요약을 부분 문자열로 검색해 일기 상세로 이동한다.

## 2. 범위
### 포함
- `CrisisDetector` (정규화 + 키워드 부분 일치), 파이프라인 연결, 전사 수정 시 재검사
- `CrisisNotice` 컴포저블 (고정 문구 + 109 다이얼), 결과 화면·상세 화면 적용
- 온보딩 면책 안내 한 줄
- `LIKE` 검색 (DAO, Repository), 검색 화면, 홈 진입 버튼

### 제외
- FTS4 (사용자 결정으로 `LIKE` 채택 — 일기 수천 건 규모에서 전체 스캔으로 충분, DB 스키마 v1 유지)
- 설정 화면 면책 안내 (설정 화면이 생길 때 추가)
- 감지 자동 해제, 키워드 사용자 편집

## 3. 위기 대응
### 3.1 `safety/CrisisDetector` (순수 object)
- `normalize(text)`: 문자(Letter)·숫자 외 모든 문자(공백·문장부호 등) 제거
- `PHRASES` (NFC 정규화 + 공백 제거 후 전체 텍스트에서 부분 일치): 죽고싶, 죽고만싶, 죽어버리고싶, 죽어버릴까, 살고싶지않, 극단적선택, 목숨을끊, 목을매달, 목매달, 손목을긋, 뛰어내리고싶, 살기싫, 사라지고싶, 없어지고싶, 생을마감, 유서를쓰, 유서를써, 유서를남기, 유서써, 번개탄
- `WORDS` (어절 단위 부분 일치 — 공백을 지우면 "여자 살"→"여자살"처럼 단어 경계를 넘어 오탐하므로): 자살, 자해
- 리뷰 반영: "유서"(→"유서 깊은" 오탐)·"목을매"(→"목을 매일" 오탐)는 동사 결합형으로 좁힘. "끝내고 싶다"는 사용자 결정으로 계속 제외
- `detect(text)` = 정규화 텍스트가 PHRASES 중 하나를 포함하거나, 공백으로 나눈 어절(문장부호 제거) 중 하나가 WORDS 중 하나를 포함
- 부정문("죽고 싶진 않아")도 감지됨 — 안전 쪽 오탐으로 감수. "끝내고 싶다"처럼 일상 표현과 겹치는 말은 제외

### 3.2 파이프라인
`DiaryPipeline.run`: 전사 → `ruleCrisis = CrisisDetector.detect(text)` → Gemma 분석 → `result.copy(crisis = ruleCrisis || result.crisis)`.
Gemma 분석은 요약·감정이 필요하므로 감지 여부와 무관하게 실행한다. 저장은 기존대로 진행.
분석 엔진이 예외를 던져도(로드 OOM 등) 파이프라인은 `parseOk=false` 결과(`crisis = ruleCrisis`)를 반환해 전사문만 저장(failed)하고 위기 안내를 표시한다.

### 3.3 표시
- `ui/CrisisNotice.kt`: 고정 문구
  "지금 많이 힘드신 것 같아요. 혼자 견디지 않으셔도 됩니다. 자살예방 상담전화 109(24시간)에서 이야기를 들어드려요."
  + [109 전화하기] → `Intent(ACTION_DIAL, "tel:109")` (CALL_PHONE 권한 불필요)
- `PipelineScreen`, `DetailScreen`: `crisis == true`이면 "공감: reply" 대신 `CrisisNotice`

### 3.4 전사 수정
`DiaryRepository.updateTranscript(id, text)`: 기존 엔트리를 읽어 `crisis = 기존 || detect(text)`로 함께 저장.
`EntryDao.updateTranscript(id, transcript, crisis)`는 SQL에서 `crisis = (crisis OR :crisis)` — 감지는 켜기만 하고, 읽기-쓰기 경합 없음.

### 3.5 온보딩
"이 앱은 상담이나 진단을 하지 않습니다. 위급할 때는 자살예방 상담전화 109로 연락하세요." 한 줄.

## 4. 검색
- `data/SearchQuery.toLikePattern(q): String?` — trim, 빈 문자열이면 null, `\`→`\\`, `%`→`\%`, `_`→`\_`, 앞뒤 `%`
- `EntryDao.search(pattern)`:
  `SELECT * FROM entries WHERE transcript LIKE :pattern ESCAPE '\' OR summary LIKE :pattern ESCAPE '\' ORDER BY created_at DESC`
- `DiaryRepository.search(q): Flow<List<EntryEntity>>` — 패턴이 null이면 `flowOf(emptyList())`
- `ui/SearchSnippet.around(text, q, radius = 20): String` — 대소문자 무시로 첫 일치 위치 앞뒤 `radius`자, 잘린 쪽에 `…`. 일치 없으면 앞 `2*radius`자
- `ui/SearchViewModel`: `query` StateFlow → 300ms debounce → `flatMapLatest { repo.search(q) }` (DAO가 `Flow` 반환, 삭제·수정 반영) → `SearchResults(query, entries)` (결과를 만든 검색어와 함께)
- `ui/SearchScreen`: 입력창 + 결과 목록(날짜 · 감정 · 스니펫, 전사문에 없으면 요약 기준), 항목 탭 → `detail/{id}`, 뒤로
- `HomeScreen`에 [검색] 버튼, `AppNavHost`에 `search` 경로

## 5. 테스트 (JVM)
- `CrisisDetectorTest`: 띄어쓰기·문장부호 변형 감지, 일상 문장 미감지
- `DiaryPipelineTest`: LLM `crisis=false`라도 규칙 감지 시 `true`, 둘 다 false면 false
- `DiaryRepositoryTest`: 수정 시 감지 설정, 기존 true 유지; 검색 빈 쿼리 → 빈 리스트, 패턴 전달
- `SearchQueryTest`: 이스케이프, 빈 입력
- `SearchSnippetTest`: 앞/뒤 경계, 미일치, 대소문자
- Room `LIKE ... ESCAPE` 실제 동작은 기기 확인 항목
