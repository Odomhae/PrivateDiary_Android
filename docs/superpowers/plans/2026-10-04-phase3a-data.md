# 3단계-a 데이터 계층 Implementation Plan

> Executed inline by the controller. Pure logic TDD via `./gradlew :app:testDebugUnitTest`; Room entities/DAO/DB validated by KSP during `compileDebugKotlin`. Branch: `feat/phase3-data`.

**Goal:** 스펙 §6 데이터 모델을 Room으로 구현하고 파이프라인 분석 결과를 영속화한다.

**Architecture:** Room 엔티티/DAO/DB는 KSP 컴파일 검증 스캐폴드. `Converters`/`EntryMapper`/`DiaryRepository`(fake DAO)는 JVM TDD. `DiaryRepository`는 `EntryDao` 인터페이스에만 의존해 테스트 가능.

**Tech Stack:** Kotlin 2.4.0, Room ~2.8.5, KSP, kotlinx.serialization, java.time(minSdk 26).

## Global Constraints
- 패키지 `com.odom.privatediary.data`. 단일 모듈.
- Room 쿼리 컬럼명은 `@ColumnInfo`와 정확히 일치(`created_at`, `duration_sec`, `audio_path`, `analysis_status`).
- 암호화·FTS·DataStore·화면은 범위 밖.
- 커밋 trailer: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- 검증은 컨트롤러가 메인 세션에서 Gradle 실행.

---

### Task 1: 빌드 — KSP + Room
- settings/catalog versions: `room = "2.8.5"`, `ksp = "2.4.0-2.0.2"`(해석 실패 시 Kotlin 2.4.0용 실제 KSP로 교정).
- libraries: `androidx-room-runtime`(group androidx.room,name room-runtime), `androidx-room-ktx`(room-ktx), `androidx-room-compiler`(room-compiler) all version.ref room.
- plugins: `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`.
- app/build.gradle.kts: `plugins { alias(libs.plugins.ksp) }`; deps `implementation(libs.androidx.room.runtime)`, `implementation(libs.androidx.room.ktx)`, `ksp(libs.androidx.room.compiler)`.
- 검증: `./gradlew :app:dependencies` 로 room 해석. KSP 적용은 Task 전체 컴파일에서 검증.
- Commit: `build: add Room + KSP`.

### Task 2: Entities (스캐폴드)
`data/EntryEntity.kt`, `data/WeeklyReportEntity.kt` — §3 표 그대로. `causes`/`keywords`는 `List<String>`.

### Task 3: Converters (순수/TDD)
`data/Converters.kt` + test. List↔JSON(kotlinx.serialization), blank→empty.

### Task 4: EntryMapper (순수/TDD)
`data/EntryMapper.kt` + test. parseOk→done/failed, intensity/summary/reply null 처리, causes/keywords passthrough.

### Task 5: DAO + Database (스캐폴드, KSP 검증)
`data/EntryDao.kt`, `data/WeeklyReportDao.kt`, `data/DiaryDatabase.kt`.

### Task 6: DiaryRepository (순수/TDD, fake DAO)
`data/DiaryRepository.kt` + test. saveEntry가 UUID·date(YYYY-MM-DD, 주입 가능한 now/idGen/zone) 생성 후 mapper로 매핑해 upsert. byDate/byId/delete/observe 위임.

### Task 7: 파이프라인 영속화 (스캐폴드)
`PipelineViewModel`에 `DiaryRepository`(DiaryDatabase.get(app).entryDao()) 추가. 파이프라인 결과를 `durationSec = pcm.size / (16000*2)`로 `saveEntry(transcript, result, durationSec)`, DB 오류는 삼켜 분석 표시 유지.

전체 검증: `./gradlew :app:testDebugUnitTest`(KSP Room 스키마 검증 + 순수 테스트). 성공 시 커밋.

## Self-Review
- 스펙 §6 Entry/WeeklyReport → T2; 매핑 T4; 영속화 T7; 순수 테스트 T3/T4/T6; 빌드 T1; Room 검증 T5(컴파일).
- FTS/암호화/DataStore/화면 제외 확인.
