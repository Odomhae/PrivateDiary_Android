# 3단계-b 화면 — 설계 문서

- 작성일: 2026-10-05
- 대상: `voice-diary-spec.md` §8 3단계의 화면 부분. §7 화면 정의.
- 선행: 3a 데이터 계층 완료 (`DiaryRepository`)
- 상태: 승인됨

## 1. 목적
`DiaryRepository`의 데이터를 보여주는 홈·결과·캘린더·상세 화면과 Navigation Compose 내비게이션을 구현한다.

## 2. 범위
### 포함
- Navigation Compose: `home`, `record`, `calendar`, `detail/{entryId}`
- 홈(녹음 버튼 + 최근 일기 리스트), 캘린더(월 그리드·감정 색), 상세(보기·전사문 수정·삭제)
- 결과/녹음은 기존 `PipelineScreen`을 `record` 라우트로 편입(완료 시 자동 저장)
- 순수 로직: 월 그리드 계산, 감정→색 매핑, 날짜별 그룹핑

### 제외 (후속)
- **재분석**(수정 전사문 재분석) — 5단계
- 앱 잠금·검색·설정·음성 재생 — 4/5단계
- 캘린더 라이브러리(직접 구현)

## 3. 구성 (패키지 `com.odom.privatediary.ui`)
**순수 로직 (JVM TDD):**
- `EmotionPalette` — `argb(emotion: String?): Long` 고정 매핑(7 감정 + null→중립). Compose가 `Color(argb)`로 변환. (Color 대신 Long이라 JVM 테스트 가능)
- `CalendarMonth` — `cells(year, month): List<CalendarCell>` (앞 공백 + 날짜, 일요일 시작, java.time)
- `EntryGrouping` — `byDate(entries): Map<String,List<EntryEntity>>`, `dominantEmotion(entries): String?`(최신 createdAt의 감정)

**스캐폴드 (컴파일 + 기기 시각 검증):**
- `AppNavHost` — NavHost
- `HomeScreen`/`HomeViewModel` — 녹음 버튼, 최근 일기(`repo.observeEntries()`)
- `CalendarScreen`/`CalendarViewModel` — 월 그리드, 날짜별 색, 월 이동
- `DetailScreen`/`DetailViewModel` — 엔트리 로드(`entry(id)`), 전사문 수정(`updateTranscript`), 삭제(`deleteEntry`)
- `MainActivity` → `AppNavHost`. 기존 `PipelineScreen`은 `record` 라우트로 사용.

## 4. 데이터 흐름
ViewModel은 `DiaryDatabase.get(app).entryDao()`로 `DiaryRepository` 구성(수동 DI 유지). Home/Calendar는 `observeEntries()` Flow를 `StateFlow`로 수집. Detail은 id로 로드·수정·삭제 후 `popBackStack`.

## 5. 테스트 전략
- JVM TDD: `EmotionPalette`(7+null 매핑), `CalendarMonth`(셀 수·시작 공백·일수; 구체 월 2026-01/2026-02), `EntryGrouping`(그룹·대표 감정).
- 스캐폴드: Composable/NavHost/VM은 `compileDebugKotlin` 컴파일 검증. 시각·네비게이션 동작은 기기(사용자).

## 6. 빌드
- `androidx.navigation:navigation-compose` 추가(해석 실패 시 버전 교정).

## 7. 성공 기준
1. 순수 로직(색·달력·그룹핑)이 JVM 테스트로 검증됨.
2. 4개 화면 + NavHost가 컴파일되고 Repository와 배선됨.
3. 상세에서 수정/삭제가 Repository로 연결됨(기기 검증은 사용자).
4. 재분석·잠금·검색·설정은 범위 밖.

## 8. 리스크
- Compose 시각 품질은 기기에서만 확인 가능.
- navigation-compose 버전 정렬(Compose BOM과) — 해석 교정.
- 캘린더 월 이동 시 전체 엔트리 관찰 후 메모리 그룹핑(일기 규모상 충분; 대량화 시 쿼리 범위화는 후속).
