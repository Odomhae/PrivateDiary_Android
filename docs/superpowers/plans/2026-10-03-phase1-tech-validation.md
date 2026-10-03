# 1단계 기술 검증 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:app`에 전사(whisper-android AAR)·분석(LiteRT-LM) 런타임을 연결하고 지연시간·메모리를 측정하는 벤치 스캐폴드를 만든다. 실기기에서 모델만 넣으면 Whisper Small과 Gemma 3n E2B를 각각 실행·측정할 수 있는 상태가 목표다.

**Architecture:** 순수 Kotlin 로직(스키마 검증, JSON 파싱, 프롬프트, 재시도, CSV)은 인터페이스 뒤에 두고 JVM 단위 테스트로 TDD한다. 런타임/기기 결합 코드(whisper·LiteRT-LM 구현, PSS 측정, ViewModel, Compose)는 확정된 실제 API로 스캐폴드를 만들고, 수치 검증은 기기에서 수행한다. `Transcriber`/`DiaryAnalyzer` 인터페이스로 런타임 교체 가능성을 보존한다.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (Material 3), kotlinx.serialization, Coroutines, `dev.ffmpegkit-maintained:whisper-android:1.0.0`, `com.google.ai.edge.litertlm:litertlm-android`.

## Global Constraints

- 플랫폼 Android 전용, 언어 Kotlin, UI Jetpack Compose + 단일 Activity.
- ABI는 **arm64-v8a only** (`ndk.abiFilters += "arm64-v8a"`).
- **두 모델을 동시에 메모리에 올리지 않는다.** Whisper 실행·해제 후 Gemma 로드. 코드상 `running` 가드 + `finally` release로 보장.
- 모델 파일은 저장소에 **커밋하지 않는다.** 앱 내부 저장소(`filesDir/models`)에 두고 `adb push`로 사이드로드.
- 1단계 스캐폴드는 **네트워크 호출 없음** (모델 수동 사이드로드). `INTERNET`/`RECORD_AUDIO` 권한 추가하지 않음.
- 고정 라벨: emotion ∈ `기쁨|평온|설렘|슬픔|불안|분노|피곤`, causes ∈ `일,관계,건강,수면,돈,자기계발,기타` (0~3개), intensity 1~5, keywords 최대 3개. (spec §3.4)
- LLM 모델은 **Gemma 3n E2B**(`.litertlm`), 전사는 Whisper Small ggml. (spec의 "Gemma 4 E2B"는 명칭 오류)
- 패키지 루트: `com.odom.privatediary.bench`.
- **이 프로젝트는 git 저장소가 아니다.** 각 Task 끝의 commit 단계는 선택적 체크포인트다. 커밋을 쓰려면 한 번 `git init` 하고, 아니면 논리적 구분점으로만 취급한다.
- **빌드/기기 검증 분담:** JVM 단위 테스트는 `./gradlew :app:testDebugUnitTest`(Windows: `.\gradlew.bat`)로 기기 없이 실행 가능. 실제 APK 빌드 통과·모델 실행·속도/메모리/한국어 품질 수치는 실기기(arm64, 8GB+ RAM)에서 사용자가 수행한다.

---

### Task 1: 빌드 설정 — 의존성 · 버전 카탈로그 · abiFilters

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: 없음
- Produces: 이후 모든 Task가 쓰는 라이브러리(`kotlinx.serialization`, coroutines, lifecycle-viewmodel-compose, whisper-android, litertlm-android)와 serialization 플러그인, arm64 abiFilter.

- [ ] **Step 1: 버전 카탈로그에 버전·라이브러리·플러그인 추가**

`gradle/libs.versions.toml`의 `[versions]`에 추가:
```toml
kotlinxSerialization = "1.8.1"
coroutines = "1.10.2"
whisperAndroid = "1.0.0"
litertlm = "0.8.0"
```
`[libraries]`에 추가:
```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
whisper-android = { group = "dev.ffmpegkit-maintained", name = "whisper-android", version.ref = "whisperAndroid" }
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
```
`[plugins]`에 추가:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

> ⚠ `litertlm` 버전은 Maven Central에서 최신 안정 버전을 확인해 핀한다(문서 예시는 `latest.release`). `0.8.0`은 자리표시용 기본값이니 빌드 동기화 시 실제 존재 버전으로 교정한다.

- [ ] **Step 2: 앱 빌드 스크립트에 플러그인·의존성·abiFilter 추가**

`app/build.gradle.kts`의 `plugins { }` 블록에 추가:
```kotlin
    alias(libs.plugins.kotlin.serialization)
```
`android { defaultConfig { } }` 안에 추가:
```kotlin
        ndk { abiFilters += "arm64-v8a" }
```
`dependencies { }`에 추가:
```kotlin
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.whisper.android)
    implementation(libs.litertlm.android)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: 의존성 해석 검증**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath` (Windows: `.\gradlew.bat ...`)
Expected: `kotlinx-serialization-json`, `whisper-android`, `litertlm-android`가 트리에 나타나고 오류 없이 종료. 해석 실패 시 해당 라이브러리의 실제 최신 버전으로 카탈로그를 교정 후 재실행.

- [ ] **Step 4: Commit (선택)**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add serialization, coroutines, whisper-android, litertlm deps + arm64 abiFilter"
```

---

### Task 2: 고정 라벨 스키마 + 결과 타입

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/DiarySchema.kt`
- Create: `app/src/main/java/com/odom/privatediary/bench/BenchTypes.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/DiarySchemaTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `DiarySchema.EMOTIONS: List<String>` (7개), `DiarySchema.CAUSES: List<String>` (7개)
  - `DiarySchema.normalizeEmotion(value: String?): String?`
  - `DiarySchema.normalizeCauses(values: List<String>): List<String>`
  - `DiarySchema.clampIntensity(value: Int): Int`
  - `data class TranscriptResult(text: String, latencyMs: Long, peakRssKb: Long)`
  - `data class AnalysisResult(summary: String, emotion: String?, intensity: Int, causes: List<String>, keywords: List<String>, reply: String, crisis: Boolean, raw: String, parseOk: Boolean, latencyMs: Long = 0, peakRssKb: Long = 0)`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/privatediary/bench/DiarySchemaTest.kt`:
```kotlin
package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiarySchemaTest {
    @Test fun emotion_valid_is_kept() {
        assertEquals("불안", DiarySchema.normalizeEmotion(" 불안 "))
    }

    @Test fun emotion_invalid_is_null() {
        assertNull(DiarySchema.normalizeEmotion("짜증"))
        assertNull(DiarySchema.normalizeEmotion(null))
    }

    @Test fun causes_filtered_deduped_and_capped_at_3() {
        val input = listOf("일", "엉뚱", "관계", "일", "수면", "돈")
        assertEquals(listOf("일", "관계", "수면"), DiarySchema.normalizeCauses(input))
    }

    @Test fun intensity_is_clamped_to_1_5() {
        assertEquals(1, DiarySchema.clampIntensity(0))
        assertEquals(5, DiarySchema.clampIntensity(9))
        assertEquals(3, DiarySchema.clampIntensity(3))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.DiarySchemaTest"`
Expected: FAIL — `DiarySchema` 미정의 컴파일 오류.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/DiarySchema.kt`:
```kotlin
package com.odom.privatediary.bench

object DiarySchema {
    val EMOTIONS = listOf("기쁨", "평온", "설렘", "슬픔", "불안", "분노", "피곤")
    val CAUSES = listOf("일", "관계", "건강", "수면", "돈", "자기계발", "기타")

    fun normalizeEmotion(value: String?): String? =
        value?.trim()?.takeIf { it in EMOTIONS }

    fun normalizeCauses(values: List<String>): List<String> =
        values.map { it.trim() }.filter { it in CAUSES }.distinct().take(3)

    fun clampIntensity(value: Int): Int = value.coerceIn(1, 5)
}
```

`app/src/main/java/com/odom/privatediary/bench/BenchTypes.kt`:
```kotlin
package com.odom.privatediary.bench

data class TranscriptResult(
    val text: String,
    val latencyMs: Long,
    val peakRssKb: Long,
)

data class AnalysisResult(
    val summary: String,
    val emotion: String?,
    val intensity: Int,
    val causes: List<String>,
    val keywords: List<String>,
    val reply: String,
    val crisis: Boolean,
    val raw: String,
    val parseOk: Boolean,
    val latencyMs: Long = 0,
    val peakRssKb: Long = 0,
)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.DiarySchemaTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/DiarySchema.kt app/src/main/java/com/odom/privatediary/bench/BenchTypes.kt app/src/test/java/com/odom/privatediary/bench/DiarySchemaTest.kt
git commit -m "feat(bench): fixed-label schema and result types"
```

---

### Task 3: LLM 출력 JSON 추출 + 파싱

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/DiaryJson.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/DiaryJsonTest.kt`

**Interfaces:**
- Consumes: `DiarySchema.*`, `AnalysisResult` (Task 2)
- Produces:
  - `DiaryJson.extractJsonObject(raw: String): String?`
  - `DiaryJson.parse(raw: String): AnalysisResult` — 실패 시 `parseOk=false`, `raw` 보존.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/privatediary/bench/DiaryJsonTest.kt`:
```kotlin
package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryJsonTest {
    @Test fun extracts_first_balanced_object_from_prose() {
        val raw = "설명입니다 ```json\n{\"a\":{\"b\":1}}\n``` 끝"
        assertEquals("{\"a\":{\"b\":1}}", DiaryJson.extractJsonObject(raw))
    }

    @Test fun returns_null_when_no_object() {
        assertNull(DiaryJson.extractJsonObject("no json here"))
    }

    @Test fun parses_valid_output_and_normalizes() {
        val raw = """여기 결과: {"summary":"면접 대기로 불안","emotion":"불안","intensity":9,
            "causes":["일","엉뚱","관계","수면"],"keywords":["면접","결과","대기","초과"],
            "reply":"마음 졸이셨겠어요.","crisis":false}"""
        val r = DiaryJson.parse(raw)
        assertTrue(r.parseOk)
        assertEquals("면접 대기로 불안", r.summary)
        assertEquals("불안", r.emotion)
        assertEquals(5, r.intensity)                       // clamp
        assertEquals(listOf("일", "관계", "수면"), r.causes) // filter+cap
        assertEquals(3, r.keywords.size)                    // cap 3
        assertFalse(r.crisis)
    }

    @Test fun invalid_emotion_becomes_null_but_parse_ok() {
        val raw = """{"summary":"x","emotion":"짜증","intensity":2,"causes":[],"keywords":[],"reply":"","crisis":false}"""
        val r = DiaryJson.parse(raw)
        assertTrue(r.parseOk)
        assertNull(r.emotion)
    }

    @Test fun no_json_returns_parse_failed() {
        val r = DiaryJson.parse("모델이 그냥 말로 답했어요")
        assertFalse(r.parseOk)
        assertEquals("모델이 그냥 말로 답했어요", r.raw)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.DiaryJsonTest"`
Expected: FAIL — `DiaryJson` 미정의.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/DiaryJson.kt`:
```kotlin
package com.odom.privatediary.bench

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object DiaryJson {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** LLM 출력에서 첫 번째 균형 잡힌 {...} 객체 문자열을 추출. 없으면 null. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until raw.length) {
            when (raw[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return raw.substring(start, i + 1) }
            }
        }
        return null
    }

    /** 추출 → 파싱 → 고정 라벨 정규화. 실패 시 parseOk=false(raw 보존). */
    fun parse(raw: String): AnalysisResult {
        val objStr = extractJsonObject(raw) ?: return failed(raw)
        return try {
            val obj = json.parseToJsonElement(objStr) as JsonObject
            fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: ""
            fun arr(key: String) =
                obj[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            AnalysisResult(
                summary = str("summary"),
                emotion = DiarySchema.normalizeEmotion(str("emotion")),
                intensity = DiarySchema.clampIntensity(obj["intensity"]?.jsonPrimitive?.intOrNull ?: 1),
                causes = DiarySchema.normalizeCauses(arr("causes")),
                keywords = arr("keywords").map { it.trim() }.filter { it.isNotEmpty() }.take(3),
                reply = str("reply"),
                crisis = obj["crisis"]?.jsonPrimitive?.booleanOrNull ?: false,
                raw = raw,
                parseOk = true,
            )
        } catch (e: Exception) {
            failed(raw)
        }
    }

    private fun failed(raw: String) = AnalysisResult(
        summary = "", emotion = null, intensity = 1, causes = emptyList(),
        keywords = emptyList(), reply = "", crisis = false, raw = raw, parseOk = false,
    )
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.DiaryJsonTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/DiaryJson.kt app/src/test/java/com/odom/privatediary/bench/DiaryJsonTest.kt
git commit -m "feat(bench): extract and parse LLM JSON output with label normalization"
```

---

### Task 4: 프롬프트 빌더 (스키마 + few-shot)

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/DiaryPrompt.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/DiaryPromptTest.kt`

**Interfaces:**
- Consumes: `DiarySchema.EMOTIONS`, `DiarySchema.CAUSES` (Task 2)
- Produces:
  - `DiaryPrompt.SYSTEM_INSTRUCTION: String`
  - `DiaryPrompt.buildUserPrompt(transcript: String): String`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/privatediary/bench/DiaryPromptTest.kt`:
```kotlin
package com.odom.privatediary.bench

import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryPromptTest {
    @Test fun system_instruction_lists_all_labels() {
        val s = DiaryPrompt.SYSTEM_INSTRUCTION
        DiarySchema.EMOTIONS.forEach { assertTrue("emotion $it 누락", s.contains(it)) }
        DiarySchema.CAUSES.forEach { assertTrue("cause $it 누락", s.contains(it)) }
    }

    @Test fun user_prompt_contains_transcript_and_fewshot() {
        val p = DiaryPrompt.buildUserPrompt("오늘은 좀 피곤했어")
        assertTrue(p.contains("오늘은 좀 피곤했어"))
        assertTrue(p.contains("전사문:"))
        assertTrue(p.contains("출력:"))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.DiaryPromptTest"`
Expected: FAIL — `DiaryPrompt` 미정의.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/DiaryPrompt.kt`:
```kotlin
package com.odom.privatediary.bench

object DiaryPrompt {
    val SYSTEM_INSTRUCTION = """
        당신은 한국어 음성 일기를 분석하는 보조자입니다.
        전사문을 읽고 아래 JSON 객체 하나만 출력하세요. 설명이나 코드펜스는 쓰지 않습니다.
        emotion은 다음 7개 중 하나만: ${DiarySchema.EMOTIONS.joinToString(" | ")}.
        causes는 다음 중 0~3개만: ${DiarySchema.CAUSES.joinToString(", ")}.
        intensity는 1~5 정수. keywords는 자유 태그 최대 3개.
        reply는 조언 없이 들은 내용을 되짚는 공감 1~2문장.
        스키마: {"summary","emotion","intensity","causes","keywords","reply","crisis"}
    """.trimIndent()

    private val FEW_SHOT = listOf(
        "오늘 드디어 이사했어. 짐 정리하느라 몸은 힘든데 새 집이라 설레네." to
            """{"summary":"이사 마치고 설렘","emotion":"설렘","intensity":4,"causes":["관계"],"keywords":["이사"],"reply":"새 집으로 옮기느라 고생 많으셨겠어요. 설레는 마음이 느껴져요.","crisis":false}""",
        "회사에서 상사한테 깨졌어. 잠도 못 잤고 머리가 아파." to
            """{"summary":"직장 스트레스로 지침","emotion":"피곤","intensity":3,"causes":["일","수면"],"keywords":["상사"],"reply":"혼나고 잠까지 설치셨다니 많이 지치셨겠어요.","crisis":false}""",
        "면접 결과를 기다리는데 너무 불안해." to
            """{"summary":"면접 결과 대기로 불안","emotion":"불안","intensity":4,"causes":["일"],"keywords":["면접"],"reply":"결과를 기다리며 마음 졸이고 계시는군요.","crisis":false}""",
    )

    fun buildUserPrompt(transcript: String): String = buildString {
        appendLine("다음은 예시입니다.")
        FEW_SHOT.forEach { (input, output) ->
            appendLine("전사문: $input")
            appendLine("출력: $output")
        }
        appendLine()
        appendLine("이제 아래 전사문을 같은 형식의 JSON으로 분석하세요.")
        appendLine("전사문: $transcript")
        append("출력: ")
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.DiaryPromptTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/DiaryPrompt.kt app/src/test/java/com/odom/privatediary/bench/DiaryPromptTest.kt
git commit -m "feat(bench): diary analysis prompt with schema and few-shot"
```

---

### Task 5: 분석 재시도 오케스트레이션

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/AnalysisRunner.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/AnalysisRunnerTest.kt`

**Interfaces:**
- Consumes: `DiaryPrompt.buildUserPrompt`, `DiaryJson.parse`, `AnalysisResult` (Tasks 2–4)
- Produces:
  - `suspend AnalysisRunner.analyzeWithRetry(transcript: String, generate: suspend (String) -> String): AnalysisResult` — 파싱 실패 시 1회 재시도. 둘 다 실패하면 마지막 출력으로 `parseOk=false` 반환.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/privatediary/bench/AnalysisRunnerTest.kt`:
```kotlin
package com.odom.privatediary.bench

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRunnerTest {
    private val valid =
        """{"summary":"s","emotion":"평온","intensity":2,"causes":[],"keywords":[],"reply":"r","crisis":false}"""

    @Test fun returns_on_first_success_without_retry() = runTest {
        var calls = 0
        val r = AnalysisRunner.analyzeWithRetry("t") { calls++; valid }
        assertTrue(r.parseOk)
        assertEquals(1, calls)
    }

    @Test fun retries_once_then_succeeds() = runTest {
        var calls = 0
        val r = AnalysisRunner.analyzeWithRetry("t") { calls++; if (calls == 1) "garbage" else valid }
        assertTrue(r.parseOk)
        assertEquals(2, calls)
    }

    @Test fun both_fail_returns_parse_failed_after_two_calls() = runTest {
        var calls = 0
        val r = AnalysisRunner.analyzeWithRetry("t") { calls++; "nope" }
        assertFalse(r.parseOk)
        assertEquals(2, calls)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.AnalysisRunnerTest"`
Expected: FAIL — `AnalysisRunner` 미정의.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/AnalysisRunner.kt`:
```kotlin
package com.odom.privatediary.bench

object AnalysisRunner {
    /** generate(prompt) → JSON 파싱. 실패 시 1회 재시도. */
    suspend fun analyzeWithRetry(
        transcript: String,
        generate: suspend (String) -> String,
    ): AnalysisResult {
        val prompt = DiaryPrompt.buildUserPrompt(transcript)
        val first = DiaryJson.parse(generate(prompt))
        if (first.parseOk) return first
        return DiaryJson.parse(generate(prompt))
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.AnalysisRunnerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/AnalysisRunner.kt app/src/test/java/com/odom/privatediary/bench/AnalysisRunnerTest.kt
git commit -m "feat(bench): analyze-with-retry orchestration"
```

---

### Task 6: 벤치 측정 레코드 + CSV

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/BenchResult.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/BenchResultTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `data class BenchRecord(model: String, inputId: String, latencyMs: Long, peakRssKb: Long, ok: Boolean, note: String = "")`
  - `BenchRecord.toCsvRow(): String`
  - `BenchRecord.Companion.CSV_HEADER: String`
  - `fun BenchRecord.appendTo(csv: java.io.File)` — 파일 없으면 헤더부터 기록.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/privatediary/bench/BenchResultTest.kt`:
```kotlin
package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BenchResultTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun csv_row_formats_and_sanitizes_commas() {
        val row = BenchRecord("gemma", "rec01", 1234, 567, true, "a,b").toCsvRow()
        assertEquals("gemma,rec01,1234,567,true,a;b", row)
    }

    @Test fun append_writes_header_once_then_rows() {
        val csv = File(tmp.root, "bench.csv")
        BenchRecord("whisper", "rec01", 10, 20, true).appendTo(csv)
        BenchRecord("gemma", "rec01", 30, 40, false).appendTo(csv)
        val lines = csv.readLines()
        assertEquals(BenchRecord.CSV_HEADER, lines[0])
        assertEquals("whisper,rec01,10,20,true,", lines[1])
        assertEquals("gemma,rec01,30,40,false,", lines[2])
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.BenchResultTest"`
Expected: FAIL — `BenchRecord` 미정의.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/BenchResult.kt`:
```kotlin
package com.odom.privatediary.bench

import java.io.File

data class BenchRecord(
    val model: String,
    val inputId: String,
    val latencyMs: Long,
    val peakRssKb: Long,
    val ok: Boolean,
    val note: String = "",
) {
    fun toCsvRow(): String = listOf(
        model, inputId, latencyMs.toString(), peakRssKb.toString(), ok.toString(),
        note.replace(",", ";"),
    ).joinToString(",")

    companion object {
        const val CSV_HEADER = "model,inputId,latencyMs,peakRssKb,ok,note"
    }
}

fun BenchRecord.appendTo(csv: File) {
    if (!csv.exists()) csv.writeText(BenchRecord.CSV_HEADER + "\n")
    csv.appendText(toCsvRow() + "\n")
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.BenchResultTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/BenchResult.kt app/src/test/java/com/odom/privatediary/bench/BenchResultTest.kt
git commit -m "feat(bench): CSV bench record"
```

---

### Task 7: 모델 경로 확인

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/ModelPaths.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/ModelPathsTest.kt`

**Interfaces:**
- Consumes: 없음 (코어 로직은 `java.io.File`만)
- Produces:
  - `class ModelPaths(modelsDir: File)` with `whisperModel: File`, `gemmaModel: File`, `whisperReady(): Boolean`, `gemmaReady(): Boolean`
  - `ModelPaths.Companion.from(context: android.content.Context): ModelPaths` (filesDir/models)

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/privatediary/bench/ModelPathsTest.kt`:
```kotlin
package com.odom.privatediary.bench

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelPathsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun not_ready_when_files_absent() {
        val p = ModelPaths(tmp.root)
        assertFalse(p.whisperReady())
        assertFalse(p.gemmaReady())
    }

    @Test fun ready_when_nonempty_files_present() {
        File(tmp.root, "ggml-small.bin").writeText("x")
        File(tmp.root, "gemma-3n-E2B-it.litertlm").writeText("y")
        val p = ModelPaths(tmp.root)
        assertTrue(p.whisperReady())
        assertTrue(p.gemmaReady())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.ModelPathsTest"`
Expected: FAIL — `ModelPaths` 미정의.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/ModelPaths.kt`:
```kotlin
package com.odom.privatediary.bench

import android.content.Context
import java.io.File

class ModelPaths(modelsDir: File) {
    val whisperModel: File = File(modelsDir, "ggml-small.bin")
    val gemmaModel: File = File(modelsDir, "gemma-3n-E2B-it.litertlm")

    fun whisperReady(): Boolean = whisperModel.exists() && whisperModel.length() > 0
    fun gemmaReady(): Boolean = gemmaModel.exists() && gemmaModel.length() > 0

    companion object {
        fun from(context: Context): ModelPaths =
            ModelPaths(File(context.filesDir, "models").apply { mkdirs() })
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.ModelPathsTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/ModelPaths.kt app/src/test/java/com/odom/privatediary/bench/ModelPathsTest.kt
git commit -m "feat(bench): model path resolution and readiness"
```

---

### Task 8: 측정 유틸 + 런타임 인터페이스

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/Benchmark.kt`
- Create: `app/src/main/java/com/odom/privatediary/bench/Runtimes.kt`
- Test: `app/src/test/java/com/odom/privatediary/bench/BenchmarkTest.kt`

**Interfaces:**
- Consumes: `TranscriptResult`, `AnalysisResult` (Task 2)
- Produces:
  - `inline fun <T> Benchmark.measureLatency(block: () -> T): Pair<T, Long>` (결과, 밀리초)
  - `fun Benchmark.currentPssKb(): Long` (기기 전용, `Debug.MemoryInfo`)
  - `interface Transcriber { suspend fun transcribe(wav: File, language: String = "ko"): TranscriptResult; fun release() }`
  - `interface DiaryAnalyzer { suspend fun analyze(transcript: String): AnalysisResult; fun release() }`

- [ ] **Step 1: 실패하는 테스트 작성** (순수 로직만 검증)

`app/src/test/java/com/odom/privatediary/bench/BenchmarkTest.kt`:
```kotlin
package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkTest {
    @Test fun measure_latency_returns_value_and_nonnegative_ms() {
        val (value, ms) = Benchmark.measureLatency { 21 + 21 }
        assertEquals(42, value)
        assertTrue(ms >= 0)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.BenchmarkTest"`
Expected: FAIL — `Benchmark` 미정의.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/Benchmark.kt`:
```kotlin
package com.odom.privatediary.bench

import android.os.Debug

object Benchmark {
    inline fun <T> measureLatency(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start) / 1_000_000
    }

    /** 현재 프로세스 PSS(KB). 추론 직후 호출해 피크 근삿값으로 사용. 기기에서만 유효. */
    fun currentPssKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }
}
```

`app/src/main/java/com/odom/privatediary/bench/Runtimes.kt`:
```kotlin
package com.odom.privatediary.bench

import java.io.File

interface Transcriber {
    suspend fun transcribe(wav: File, language: String = "ko"): TranscriptResult
    fun release()
}

interface DiaryAnalyzer {
    suspend fun analyze(transcript: String): AnalysisResult
    fun release()
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.odom.privatediary.bench.BenchmarkTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/Benchmark.kt app/src/main/java/com/odom/privatediary/bench/Runtimes.kt app/src/test/java/com/odom/privatediary/bench/BenchmarkTest.kt
git commit -m "feat(bench): latency/PSS measurement + runtime interfaces"
```

---

### Task 9: WhisperTranscriber (whisper-android AAR 구현, 스캐폴드)

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/WhisperTranscriber.kt`

**Interfaces:**
- Consumes: `Transcriber`, `TranscriptResult`, `Benchmark` (Tasks 2, 8); whisper-android AAR (`Whisper`, `WhisperModel`, `WhisperConfig`, `WhisperResult`)
- Produces:
  - `class WhisperTranscriber(context: Context, modelPath: String) : Transcriber` with `suspend fun load()`

> 이 Task는 기기/AAR 결합 코드라 JVM 단위 테스트 대상이 아니다. 검증은 컴파일 통과 + 기기 실행(Task 13)이다.

- [ ] **Step 1: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/WhisperTranscriber.kt`:
```kotlin
package com.odom.privatediary.bench

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File

class WhisperTranscriber(
    private val context: Context,
    private val modelPath: String,
) : Transcriber {
    private var model: WhisperModel? = null

    suspend fun load() {
        model = Whisper.loadModel(context, modelPath)
    }

    override suspend fun transcribe(wav: File, language: String): TranscriptResult {
        val m = model ?: Whisper.loadModel(context, modelPath).also { model = it }
        val start = System.nanoTime()
        val result = Whisper.transcribe(m, wav.absolutePath, WhisperConfig(language = language))
        val latencyMs = (System.nanoTime() - start) / 1_000_000
        return TranscriptResult(
            text = result.text,
            latencyMs = latencyMs,
            peakRssKb = Benchmark.currentPssKb(),
        )
    }

    override fun release() {
        model?.let { Whisper.releaseModel(it) }
        model = null
    }
}
```

> ⚠ `dev.ffmpegkit.whisper.*` import 경로와 `WhisperResult.text` 접근자는 설치된 AAR에서 실제 패키지/멤버명으로 확인·교정한다(IDE 자동완성 사용). 메서드 시그니처(`loadModel/transcribe/releaseModel`, `WhisperConfig(language=)`)는 문서 기준이다.

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. import 오류 시 AAR의 실제 패키지로 교정.

- [ ] **Step 3: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/WhisperTranscriber.kt
git commit -m "feat(bench): WhisperTranscriber over whisper-android AAR"
```

---

### Task 10: LiteRtDiaryAnalyzer (LiteRT-LM 구현, 스캐폴드)

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/LiteRtDiaryAnalyzer.kt`

**Interfaces:**
- Consumes: `DiaryAnalyzer`, `AnalysisResult`, `AnalysisRunner`, `DiaryPrompt`, `Benchmark` (Tasks 2, 4, 5, 8); LiteRT-LM (`Engine`, `EngineConfig`, `Backend`, `ConversationConfig`, `Contents`, `SamplerConfig`)
- Produces:
  - `class LiteRtDiaryAnalyzer(modelPath: String, cacheDir: String) : DiaryAnalyzer` with `suspend fun load()`

> 기기/런타임 결합 코드. 검증은 컴파일 통과 + 기기 실행(Task 13).

- [ ] **Step 1: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/LiteRtDiaryAnalyzer.kt`:
```kotlin
package com.odom.privatediary.bench

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

class LiteRtDiaryAnalyzer(
    private val modelPath: String,
    private val cacheDir: String,
) : DiaryAnalyzer {
    private var engine: Engine? = null

    /** 초기화는 최대 10초가 걸릴 수 있으므로 백그라운드 디스패처에서 호출한다. */
    suspend fun load() {
        val e = Engine(
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
        )
        e.initialize()
        engine = e
    }

    override suspend fun analyze(transcript: String): AnalysisResult {
        val e = engine ?: error("Engine not loaded; call load() first")
        val start = System.nanoTime()
        val result = AnalysisRunner.analyzeWithRetry(transcript) { prompt ->
            e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(DiaryPrompt.SYSTEM_INSTRUCTION),
                    samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.4),
                )
            ).use { conversation -> conversation.sendMessage(prompt).toString() }
        }
        val latencyMs = (System.nanoTime() - start) / 1_000_000
        return result.copy(latencyMs = latencyMs, peakRssKb = Benchmark.currentPssKb())
    }

    override fun release() {
        engine?.close()
        engine = null
    }
}
```

> ⚠ `conversation.sendMessage(prompt)`는 `Message`를 반환한다. 문자열 추출은 설치된 라이브러리의 실제 접근자로 확인한다(`toString()`이 전체 텍스트가 아니면 `.text`/`.content` 등으로 교정). 그 외 클래스·메서드명은 LiteRT-LM Kotlin 문서 기준이다.

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. import 오류 시 실제 패키지/버전으로 교정.

- [ ] **Step 3: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/LiteRtDiaryAnalyzer.kt
git commit -m "feat(bench): LiteRtDiaryAnalyzer over LiteRT-LM"
```

---

### Task 11: BenchViewModel (순차 실행, 한 번에 한 모델)

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/BenchViewModel.kt`

**Interfaces:**
- Consumes: `ModelPaths`, `WhisperTranscriber`, `LiteRtDiaryAnalyzer`, `BenchRecord`/`appendTo`, `AnalysisResult` (Tasks 2, 6, 7, 9, 10)
- Produces:
  - `data class BenchUiState(whisperReady, gemmaReady, running, transcript, analysis, log)`
  - `class BenchViewModel(app: Application) : AndroidViewModel` with `state: StateFlow<BenchUiState>`, `fun runWhisper(wav: File)`, `fun runGemma()`

> 기기 결합 코드. 검증은 컴파일 통과 + 기기 실행(Task 13). 핵심 불변식: `running != null`이면 다른 실행 거부, 추론은 `Dispatchers.Default`, 각 실행 후 `release()`로 모델 해제(동시 적재 금지).

- [ ] **Step 1: 구현 작성**

`app/src/main/java/com/odom/privatediary/bench/BenchViewModel.kt`:
```kotlin
package com.odom.privatediary.bench

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BenchUiState(
    val whisperReady: Boolean = false,
    val gemmaReady: Boolean = false,
    val running: String? = null,
    val transcript: String = "",
    val analysis: AnalysisResult? = null,
    val log: List<String> = emptyList(),
)

class BenchViewModel(app: Application) : AndroidViewModel(app) {
    private val paths = ModelPaths.from(app)
    private val csv = File(app.filesDir, "bench_results.csv")
    private val dispatcher = Dispatchers.Default

    private val _state = MutableStateFlow(
        BenchUiState(whisperReady = paths.whisperReady(), gemmaReady = paths.gemmaReady())
    )
    val state: StateFlow<BenchUiState> = _state.asStateFlow()

    fun runWhisper(wav: File) {
        if (_state.value.running != null) return
        _state.value = _state.value.copy(running = "whisper")
        viewModelScope.launch {
            val t = WhisperTranscriber(getApplication(), paths.whisperModel.absolutePath)
            try {
                val result = withContext(dispatcher) { t.load(); t.transcribe(wav) }
                BenchRecord("whisper", wav.name, result.latencyMs, result.peakRssKb, true).appendTo(csv)
                _state.value = _state.value.copy(
                    transcript = result.text,
                    running = null,
                    log = _state.value.log + "whisper ${result.latencyMs}ms pss=${result.peakRssKb}KB",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(running = null, log = _state.value.log + "whisper ERROR ${e.message}")
            } finally {
                withContext(dispatcher) { t.release() }
            }
        }
    }

    fun runGemma() {
        val transcript = _state.value.transcript
        if (_state.value.running != null || transcript.isBlank()) return
        _state.value = _state.value.copy(running = "gemma")
        viewModelScope.launch {
            val a = LiteRtDiaryAnalyzer(
                paths.gemmaModel.absolutePath,
                getApplication<Application>().cacheDir.absolutePath,
            )
            try {
                val result = withContext(dispatcher) { a.load(); a.analyze(transcript) }
                BenchRecord("gemma", "transcript", result.latencyMs, result.peakRssKb, result.parseOk).appendTo(csv)
                _state.value = _state.value.copy(
                    analysis = result,
                    running = null,
                    log = _state.value.log + "gemma ${result.latencyMs}ms pss=${result.peakRssKb}KB ok=${result.parseOk}",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(running = null, log = _state.value.log + "gemma ERROR ${e.message}")
            } finally {
                withContext(dispatcher) { a.release() }
            }
        }
    }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/BenchViewModel.kt
git commit -m "feat(bench): sequential bench view model (one model in memory at a time)"
```

---

### Task 12: BenchScreen + MainActivity 교체

**Files:**
- Create: `app/src/main/java/com/odom/privatediary/bench/BenchScreen.kt`
- Modify: `app/src/main/java/com/odom/privatediary/MainActivity.kt`

**Interfaces:**
- Consumes: `BenchViewModel`, `BenchUiState` (Task 11), `ModelPaths` (Task 7)
- Produces: `@Composable fun BenchScreen(vm: BenchViewModel)`; `MainActivity`가 이를 호스팅.

> UI 스캐폴드. 검증은 컴파일 통과 + 기기 실행(Task 13). WAV 입력은 `filesDir/samples/*.wav`를 adb push로 넣고 첫 파일을 사용.

- [ ] **Step 1: BenchScreen 작성**

`app/src/main/java/com/odom/privatediary/bench/BenchScreen.kt`:
```kotlin
package com.odom.privatediary.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun BenchScreen(vm: BenchViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Phase 1 Bench")
        Text("whisper 모델: ${if (state.whisperReady) "준비됨" else "없음 (adb push 필요)"}")
        Text("gemma 모델: ${if (state.gemmaReady) "준비됨" else "없음 (adb push 필요)"}")
        Text(if (state.running != null) "실행 중: ${state.running}" else "대기")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.whisperReady && state.running == null,
                onClick = {
                    val wav = File(File(context.filesDir, "samples"), "sample01.wav")
                    vm.runWhisper(wav)
                },
            ) { Text("Whisper 실행") }

            Button(
                enabled = state.gemmaReady && state.running == null && state.transcript.isNotBlank(),
                onClick = { vm.runGemma() },
            ) { Text("Gemma 실행") }
        }

        if (state.transcript.isNotBlank()) Text("전사: ${state.transcript}")
        state.analysis?.let { Text("분석(ok=${it.parseOk}): ${it.raw}") }
        state.log.forEach { Text(it) }
    }
}
```

- [ ] **Step 2: MainActivity를 BenchScreen 호스팅으로 교체**

`app/src/main/java/com/odom/privatediary/MainActivity.kt` 전체를 교체:
```kotlin
package com.odom.privatediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odom.privatediary.bench.BenchScreen
import com.odom.privatediary.bench.BenchViewModel
import com.odom.privatediary.ui.theme.PrivateDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateDiaryTheme {
                val vm: BenchViewModel = viewModel()
                BenchScreen(vm)
            }
        }
    }
}
```

> ⚠ 테마 컴포저블 이름은 `ui/theme/Theme.kt`의 실제 이름(`PrivateDiaryTheme`로 추정)과 일치시킨다. `collectAsStateWithLifecycle`에는 `androidx.lifecycle:lifecycle-runtime-compose`가 필요하다 — Task 1에서 추가한 `lifecycle-viewmodel-compose`로 당겨지지 않으면 `libs.versions.toml`에 `androidx-lifecycle-runtime-compose`(동일 버전)를 추가하고 `implementation`에 넣는다.

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (선택)**

```bash
git add app/src/main/java/com/odom/privatediary/bench/BenchScreen.kt app/src/main/java/com/odom/privatediary/MainActivity.kt
git commit -m "feat(bench): bench screen and MainActivity host"
```

---

### Task 13: 모델 획득·측정 가이드 (MODELS.md) + 기기 검증

**Files:**
- Create: `MODELS.md`

**Interfaces:**
- Consumes: 전체 앱 (Tasks 1–12)
- Produces: 모델 사이드로드 절차 + 10개 녹음 측정 기록 템플릿 + 런타임·포맷 확정 판단표.

- [ ] **Step 1: MODELS.md 작성**

`MODELS.md`:
````markdown
# 모델 준비 · 측정 가이드 (1단계)

모델 파일은 저장소에 커밋하지 않는다. 아래 절차로 기기에 직접 넣는다.

## 1. 모델 내려받기
- Whisper Small ggml: whisper.cpp 모델 저장소에서 `ggml-small.bin` (약 466MB~).
- Gemma 3n E2B (LiteRT-LM): HuggingFace `google/gemma-3n-E2B-it-litert-lm`
  (`gemma-3n-E2B-it.litertlm`, 약 3.7GB, 실행 시 RAM 약 8GB).

## 2. 기기로 push (앱 설치 후, 패키지 com.odom.privatediary)
```bash
adb shell run-as com.odom.privatediary mkdir -p files/models files/samples
adb push ggml-small.bin /data/local/tmp/
adb push gemma-3n-E2B-it.litertlm /data/local/tmp/
adb shell "run-as com.odom.privatediary cp /data/local/tmp/ggml-small.bin files/models/"
adb shell "run-as com.odom.privatediary cp /data/local/tmp/gemma-3n-E2B-it.litertlm files/models/"
```
한국어 녹음 10개(16kHz mono WAV 권장, 아니어도 자동 리샘플됨)를 `sample01.wav`~`sample10.wav`로
`files/samples/`에 같은 방식으로 push. (`BenchScreen`은 현재 `sample01.wav`를 사용 — 녹음별로 파일명을 바꿔가며 측정)

## 3. 측정
- 앱 실행 → [Whisper 실행] → 전사 확인 → [Gemma 실행] → 분석 JSON 확인.
- 결과는 `files/bench_results.csv`에 누적. 회수:
  `adb shell run-as com.odom.privatediary cat files/bench_results.csv`
- Logcat 태그 없이 화면 로그로도 지연시간·PSS 확인 가능.

## 4. 측정 기록 (녹음 10개)
| rec | whisper ms | whisper PSS KB | 전사 품질(1~5) | gemma ms | gemma PSS KB | JSON ok | 감정 적절(1~5) |
|-----|-----------|----------------|----------------|----------|--------------|---------|----------------|
| 01  |           |                |                |          |              |         |                |
| ... |           |                |                |          |              |         |                |

## 5. 런타임·포맷 확정 판단
- [ ] whisper-android AAR 한국어 전사 품질이 쓸만한가? (아니면 whisper.cpp 직접 JNI 빌드로 전환)
- [ ] Gemma 3n E2B가 대상 기기에서 메모리·속도 허용 범위인가? (RAM ~8GB 요구 재확인)
- [ ] 감정/causes 라벨 일관성은? (같은 전사 반복 입력 시 흔들림)
- [ ] 최종 결정: LLM 런타임 = LiteRT-LM 유지 / 변경, 포맷 = `.litertlm`.
- [ ] 위 결론을 `voice-diary-spec.md` §2.2/§2.5/§3에 반영.
````

- [ ] **Step 2: 기기 검증 (사용자 수행)**

Run (기기 연결 후): `./gradlew :app:installDebug` → 앱 실행 → MODELS.md 절차대로 측정.
Expected: Whisper 전사·Gemma 분석이 각각 실행되고 `bench_results.csv`에 레코드가 쌓인다. 두 모델이 동시에 메모리에 올라가지 않음(로그상 순차 실행).

- [ ] **Step 3: Commit (선택)**

```bash
git add MODELS.md
git commit -m "docs: model side-load and phase-1 measurement guide"
```

---

## Self-Review

**1. Spec coverage (설계 문서 대비):**
- 전사(whisper)·분석(LiteRT-LM) 각 실행 → Tasks 9, 10, 11, 12 ✓
- 지연시간·메모리 측정 + CSV → Tasks 6, 8, 11, 13 ✓
- §3.4 고정 스키마 JSON + 1회 재시도 → Tasks 2, 3, 4, 5 ✓
- 두 모델 동시 적재 금지 → Task 11 (`running` 가드 + `finally` release) ✓
- 모델 커밋 금지 / adb push → Task 13 ✓
- arm64-v8a only → Task 1 ✓
- 인터페이스로 런타임 교체 가능 → Task 8 (`Transcriber`/`DiaryAnalyzer`) ✓
- 10개 녹음 측정 + 런타임 확정 판단 → Task 13 ✓
- 실기기 검증 분담 → Task 13 Step 2 ✓

**2. Placeholder scan:** 런타임 결합 코드의 import/멤버명에 "⚠ 실제 라이브러리로 확인·교정" 주석이 있으나, 이는 외부 아티팩트 표면을 IDE에서 핀하라는 지시(검증 가능한 실제 메서드 시그니처 기반)이지 로직 공백이 아니다. 순수 로직 Task(2~8)에는 자리표시 없음.

**3. Type consistency:** `AnalysisResult`(nullable `emotion`, `parseOk`, `latencyMs`/`peakRssKb` 기본값), `TranscriptResult`, `BenchRecord(toCsvRow/appendTo/CSV_HEADER)`, `ModelPaths(modelsDir | from(context))`, `Transcriber`/`DiaryAnalyzer`(`transcribe`/`analyze` + `release`), `AnalysisRunner.analyzeWithRetry(transcript, generate)`가 정의 Task와 소비 Task 간 일치함을 확인.

---

## Execution Handoff

계획이 `docs/superpowers/plans/2026-10-03-phase1-tech-validation.md`에 저장되었습니다. 실행 방식 두 가지:

1. **Subagent-Driven (권장)** — Task마다 새 subagent 디스패치, Task 사이에 리뷰, 빠른 반복.
2. **Inline Execution** — 이 세션에서 executing-plans로 체크포인트 두며 일괄 실행.

어느 방식으로 할까요?
