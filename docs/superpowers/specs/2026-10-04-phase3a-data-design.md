# 3단계-a 데이터 계층 — 설계 문서

- 작성일: 2026-10-04
- 대상: `voice-diary-spec.md` §8 3단계(저장과 화면)의 데이터 계층 부분. 화면은 3b에서.
- 선행: 1·2단계 완료 (런타임, 파이프라인)
- 상태: 승인됨

## 1. 목적
스펙 §6 데이터 모델을 Room으로 구현하고, 2단계 파이프라인의 분석 결과를 로컬 DB에 영속화한다.

## 2. 범위
### 포함
- Room `EntryEntity`, `WeeklyReportEntity` (스펙 §6)
- `List<String>` ↔ JSON TypeConverter
- 분석 결과 → `EntryEntity` 매핑
- `EntryDao` / `WeeklyReportDao`, `DiaryDatabase`
- `DiaryRepository` (DAO 인터페이스 의존 → fake로 단위 테스트)
- 파이프라인 완료 시 Entry 저장 (`PipelineViewModel` 배선)

### 의도적 제외 (후속 단계)
- **화면**(홈/결과/캘린더/상세) — 3b
- **전문 검색 FTS4** — 스펙 §8 **5단계("검색")**. (Room FTS는 KSP 컴파일 검증 리스크도 있어 해당 단계에서)
- **SQLCipher 암호화** — 스펙 §2.2 "필요 시". 지금은 평문 Room(앱 내부 저장소)
- **DataStore(설정)** — 설정 화면과 함께(5단계)

## 3. 데이터 모델 (스펙 §6)

`EntryEntity` (table `entries`):
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | TEXT PK | UUID |
| date | TEXT | YYYY-MM-DD (같은 날 복수 가능) |
| created_at | INTEGER | epoch millis |
| duration_sec | INTEGER | |
| audio_path | TEXT? | 보관 옵션 꺼지면 null |
| transcript | TEXT | |
| summary | TEXT? | |
| emotion | TEXT? | 고정 라벨 |
| intensity | INTEGER? | 1~5 |
| causes | TEXT? | JSON 배열 |
| keywords | TEXT? | JSON 배열 |
| reply | TEXT? | |
| crisis | INTEGER | default 0 (Boolean) |
| analysis_status | TEXT | pending / done / failed |

`WeeklyReportEntity` (table `weekly_reports`): id TEXT PK, week_start TEXT, body TEXT, created_at INTEGER.

## 4. 구성 (패키지 `com.odom.privatediary.data`)
```
EntryEntity.kt          -- @Entity(tableName="entries"), @ColumnInfo 매핑
WeeklyReportEntity.kt   -- @Entity(tableName="weekly_reports")
Converters.kt           -- (순수) List<String> ↔ JSON (kotlinx.serialization)
EntryMapper.kt          -- (순수) (transcript, AnalysisResult, durationSec, audioPath?, id, createdAt) → EntryEntity
EntryDao.kt             -- @Dao 인터페이스: upsert/byId/byDate/observeAll/updateTranscript/delete
WeeklyReportDao.kt      -- @Dao: upsert/observeAll
DiaryDatabase.kt        -- @Database(entities=[...], version=1), @TypeConverters
DiaryRepository.kt      -- EntryDao/WeeklyReportDao에 의존(인터페이스), 저장/조회 위임 + id/date 생성
```

### 경계
- `DiaryRepository`는 `EntryDao`(= `@Dao` 인터페이스)에만 의존 → 테스트에서 fake DAO로 대체 가능.
- 분석 결과 매핑은 순수 함수 `EntryMapper.toEntry(...)`로 분리(주입된 id·createdAt). `analysis_status`는 `AnalysisResult.parseOk`로 done/failed, `emotion==null && !parseOk`면 pending은 쓰지 않음(현 파이프라인은 분석을 끝내므로 done/failed만).

### 파이프라인 영속화
`PipelineViewModel`이 파이프라인 `Done` 후 `DiaryRepository.saveEntry(transcript, analysisResult, durationSec)` 호출. Repository가 UUID·date(YYYY-MM-DD, 기기 시간대) 생성 후 `EntryMapper`로 매핑해 `EntryDao.upsert`.

## 5. 테스트 전략
### 순수/JVM TDD (기기 불필요)
- `Converters`: 리스트↔JSON 라운드트립, 빈 리스트, null 처리.
- `EntryMapper.toEntry`: 필드 매핑, causes/keywords 직렬화, `analysis_status`(parseOk→done, !parseOk→failed), crisis Boolean.
- `DiaryRepository`: fake `EntryDao`로 saveEntry가 올바른 엔티티로 upsert 호출하는지(id 비어있지 않음, date 형식 YYYY-MM-DD), byDate/delete 위임.

### 스캐폴드 (컴파일 + KSP 스키마 검증)
`EntryEntity`/`WeeklyReportEntity`/`EntryDao`/`WeeklyReportDao`/`DiaryDatabase`는 Room KSP가 `compileDebugKotlin` 시 스키마를 검증한다(쿼리·컬럼명 정합성 포함). 실제 DB 동작(삽입/조회)은 기기 계측 테스트(사용자).

## 6. 빌드 변경
- KSP 플러그인 `com.google.devtools.ksp` (Kotlin 2.4.0 정렬 버전; 해석 실패 시 교정).
- `androidx.room:room-runtime`, `androidx.room:room-ktx`, `ksp(androidx.room:room-compiler)` (~2.8.5).
- `app/build.gradle.kts`에 ksp 플러그인 적용 + 의존성.

## 7. 성공 기준
1. `Converters`/`EntryMapper`/`DiaryRepository`가 JVM 테스트로 검증됨(fake DAO).
2. Room 엔티티/DAO/DB가 KSP 스키마 검증을 통과(`compileDebugKotlin` 성공).
3. 파이프라인 완료 시 Entry가 저장되도록 배선됨(기기 검증은 사용자).
4. 암호화·FTS·DataStore·화면은 범위에 없음(후속).

## 8. 리스크
- KSP/Room/Kotlin 2.4.0 버전 정렬 — Task 1에서 해석 교정.
- Room 쿼리의 컬럼명은 `@ColumnInfo` 이름과 정확히 일치해야 KSP 검증 통과(예: `created_at`).
- 기기 없는 환경이라 실제 DB CRUD는 미검증 — 매핑/Repository 로직만 JVM 보장.
