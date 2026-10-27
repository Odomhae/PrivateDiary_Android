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
- `KEYWORDS` (정규화된 형태):
  죽고싶, 죽어버리고싶, 자살, 자해, 극단적선택, 목숨을끊, 목을매, 손목을긋, 뛰어내리고싶, 살기싫, 사라지고싶, 없어지고싶, 유서, 생을마감
- `detect(text): Boolean` = `normalize(text)`가 키워드 중 하나를 포함
- 부정문("죽고 싶진 않아")도 감지됨 — 안전 쪽 오탐으로 감수. "끝내고 싶다"처럼 일상 표현과 겹치는 말은 제외

### 3.2 파이프라인
`DiaryPipeline.run`: 전사 → `ruleCrisis = CrisisDetector.detect(text)` → Gemma 분석 → `result.copy(crisis = ruleCrisis || result.crisis)`.
Gemma 분석은 요약·감정이 필요하므로 감지 여부와 무관하게 실행한다. 저장은 기존대로 진행.

### 3.3 표시
- `ui/CrisisNotice.kt`: 고정 문구
  "지금 많이 힘드신 것 같아요. 혼자 견디지 않으셔도 됩니다. 자살예방 상담전화 109(24시간)에서 이야기를 들어드려요."
  + [109 전화하기] → `Intent(ACTION_DIAL, "tel:109")` (CALL_PHONE 권한 불필요)
- `PipelineScreen`, `DetailScreen`: `crisis == true`이면 "공감: reply" 대신 `CrisisNotice`

### 3.4 전사 수정
`DiaryRepository.updateTranscript(id, text)`: 기존 엔트리를 읽어 `crisis = 기존 || detect(text)`로 함께 저장.
`EntryDao.updateTranscript(id, transcript, crisis)`로 시그니처 변경. 감지는 켜기만 하고 해제하지 않는다.

### 3.5 온보딩
"이 앱은 상담이나 진단을 하지 않습니다. 위급할 때는 자살예방 상담전화 109로 연락하세요." 한 줄.

## 4. 검색
- `data/SearchQuery.toLikePattern(q): String?` — trim, 빈 문자열이면 null, `\`→`\\`, `%`→`\%`, `_`→`\_`, 앞뒤 `%`
- `EntryDao.search(pattern)`:
  `SELECT * FROM entries WHERE transcript LIKE :pattern ESCAPE '\' OR summary LIKE :pattern ESCAPE '\' ORDER BY created_at DESC`
- `DiaryRepository.search(q): List<EntryEntity>` — 패턴이 null이면 빈 리스트
- `ui/SearchSnippet.around(text, q, radius = 20): String` — 대소문자 무시로 첫 일치 위치 앞뒤 `radius`자, 잘린 쪽에 `…`. 일치 없으면 앞 `2*radius`자
- `ui/SearchViewModel`: `query` StateFlow → 300ms debounce → `repo.search` → `results`
- `ui/SearchScreen`: 입력창 + 결과 목록(날짜 · 감정 · 스니펫, 전사문에 없으면 요약 기준), 항목 탭 → `detail/{id}`, 뒤로
- `HomeScreen`에 [검색] 버튼, `AppNavHost`에 `search` 경로

## 5. 테스트 (JVM)
- `CrisisDetectorTest`: 띄어쓰기·문장부호 변형 감지, 일상 문장 미감지
- `DiaryPipelineTest`: LLM `crisis=false`라도 규칙 감지 시 `true`, 둘 다 false면 false
- `DiaryRepositoryTest`: 수정 시 감지 설정, 기존 true 유지; 검색 빈 쿼리 → 빈 리스트, 패턴 전달
- `SearchQueryTest`: 이스케이프, 빈 입력
- `SearchSnippetTest`: 앞/뒤 경계, 미일치, 대소문자
- Room `LIKE ... ESCAPE` 실제 동작은 기기 확인 항목
