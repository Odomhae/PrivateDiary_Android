# 1단계 기술 검증 (Technical Validation) — 설계 문서

- 작성일: 2026-10-03
- 대상: `voice-diary-spec.md` §8 개발 단계 1단계
- 상태: 승인됨 (구현 계획 작성 대기)

## 1. 목적

빈 Compose 프로젝트(`:app`)에 **온디바이스 음성→텍스트**와 **온디바이스 LLM** 런타임을 각각 연결하여, 나중에 실제 arm64 기기에서 다음을 측정할 수 있는 상태를 만든다.

- Whisper Small 전사 실행 (속도, 피크 메모리, 한국어 품질)
- Gemma 3n E2B 분석 실행 (속도, 피크 메모리, JSON 품질, 한국어 품질)
- 한국어 일기 녹음 10개로 반복 측정
- 위 결과로 **LLM 런타임과 모델 포맷을 확정**

이 작업의 산출물은 **컴파일 가능한 통합 스캐폴드 + 측정 계측 + 실행/측정 가이드**다. 수치 측정과 빌드 통과 검증은 실기기·SDK·모델 파일이 준비된 뒤 사용자가 수행한다(아래 §8 참조).

## 2. 사전 조사로 확정된 사실

| 항목 | 결론 | 근거 |
|---|---|---|
| LLM 런타임 | **LiteRT-LM** (Google이 공식 권장, MediaPipe LLM Inference는 maintenance-only) | ai.google.dev/edge/litert-lm/android |
| LLM 통합 방식 | Gradle 의존성 `com.google.ai.edge.litertlm:litertlm-android` + `EngineConfig`/`Engine` Kotlin API. NDK 불필요 | 위 공식 문서 |
| LLM 모델 | **Gemma 3n E2B** (`google/gemma-3n-E2B-it-litert-lm`). 스펙의 "Gemma 4 E2B"는 명칭 오류 — `E2B`/`E4B`는 Gemma 3n 계열 | HuggingFace |
| 전사 런타임 | **whisper.cpp prebuilt AAR** `dev.ffmpegkit-maintained:whisper-android:1.0.0` (arm64-v8a, API 24+, NDK 불필요). 스펙의 직접 NDK/CMake 빌드 대신 채택 | Maven Central |

### 스펙과 어긋나는 중요한 발견 (후속 단계에 반영 필요)

1. **메모리/기기 요구사항 상향**: 스펙 §2.5/§3.2는 "E2B RAM 약 2GB, 기기 RAM 6GB 권장"이라 했으나, 실제 `gemma-3n-E2B-it-litert-lm`은 **파일 ~3.7GB, 실행 시 RAM ~8GB** 수준이다. 최소 지원 기기 기준을 상향해야 한다.
2. **NDK/CMake 불필요**: 두 런타임 모두 Gradle 아티팩트로 제공되어, 스펙 §2.2가 상정한 whisper.cpp JNI 직접 빌드가 이 스파이크에서는 필요 없다. (추후 전사 성능/제어가 부족하면 직접 빌드로 전환 가능하도록 인터페이스 뒤에 숨긴다.)
3. **whisper AAR 신뢰성 검증 필요**: 커뮤니티 아티팩트(프리빌트 네이티브 바이너리)이므로, 1단계 중 동작·무결성 확인을 체크 항목으로 둔다. 온디바이스/오프라인 실행이라 "음성이 기기 밖으로 나가지 않는다"는 원칙에는 부합한다.

## 3. 범위 (Scope)

### 포함
- `:app` 모듈 안에 `bench/` 패키지로 전사·분석 통합 스캐폴드
- 전사/분석을 각각 독립 실행하는 Compose 화면
- 지연시간·피크 메모리 계측 및 CSV 기록
- §3.4 JSON 스키마 프롬프트 + 파싱(1회 재시도)
- 모델 획득/사이드로드/측정 기록 가이드 문서

### 제외 (후속 단계)
- 멀티모듈 분리(`core/*`, `feature/*`) — 2단계 이후
- 실제 녹음 UI, VAD, Room 저장, 캘린더/리포트/설정 등 제품 기능
- 모델 다운로드(R2) 연동 — 1단계에서는 `adb push` 수동 사이드로드
- 앱 잠금, 위기 대응 등 MVP 기능

## 4. 구조

스파이크는 단일 모듈 `:app`을 유지한다(멀티모듈화는 2단계 이후). 신규 코드는 `bench/` 패키지에 격리한다.

```
app/src/main/java/com/odom/privatediary/
  MainActivity.kt            # BenchScreen 호스팅으로 교체 (기존 템플릿 대체)
  bench/
    Transcriber.kt           # interface: suspend fun transcribe(wav: File, lang="ko"): TranscriptResult
    DiaryAnalyzer.kt         # interface: suspend fun analyze(transcript: String): AnalysisResult
    WhisperTranscriber.kt    # whisper-android AAR 구현
    LiteRtDiaryAnalyzer.kt   # LiteRT-LM Engine 구현
    DiaryPrompt.kt           # §3.4 스키마 프롬프트 + few-shot + JSON 파싱(+1회 재시도)
    DiaryJson.kt             # AnalysisResult 데이터 클래스 + 파서 (고정 라벨 검증)
    Benchmark.kt             # measure { } : 지연시간 + 피크 PSS(Debug.MemoryInfo)
    BenchResult.kt           # 측정 레코드 + CSV 직렬화 (filesDir/bench_results.csv)
    ModelPaths.kt            # 모델 파일 위치 확인(filesDir), 부재 시 안내
    BenchViewModel.kt        # StateFlow 상태, 순차 실행(한 번에 한 모델만)
    BenchScreen.kt           # Compose: 모델 존재 여부, [Whisper 실행][Gemma 실행], 진행/결과/측정 표시
```

### 인터페이스 경계
- `Transcriber`와 `DiaryAnalyzer`는 스펙 §2.3의 교체 가능 경계를 그대로 따른다. 구현(AAR / LiteRT-LM)을 교체해도 소비자(ViewModel)는 바뀌지 않는다.
- 추론은 `Dispatchers.Default` 기반 전용 디스패처에서 실행하고, ViewModel은 상태만 관리한다.

### 데이터 타입
```kotlin
data class TranscriptResult(val text: String, val latencyMs: Long, val peakRssKb: Long)
data class AnalysisResult(
    val summary: String, val emotion: String, val intensity: Int,
    val causes: List<String>, val keywords: List<String>,
    val reply: String, val crisis: Boolean,
    val raw: String, val parseOk: Boolean,
    val latencyMs: Long, val peakRssKb: Long
)
```
`emotion`은 7개 고정 라벨, `causes`는 7개 고정 카테고리로 검증한다(스펙 §3.4). 파싱 실패 시 1회 재시도 후 `parseOk=false`로 원문만 보존한다.

## 5. 핵심 제약 반영

- **두 모델 동시 적재 금지**: `BenchViewModel`은 Whisper 실행 → 해제 → Gemma 로드 순으로만 동작시킨다. 각 실행 전후로 엔진 close/release를 명시한다.
- **arm64-v8a only**: `ndk.abiFilters = ["arm64-v8a"]`로 제한.
- **모델 파일은 저장소에 커밋하지 않는다**: 앱 전용 저장소(`filesDir`)에 둔다. `adb push` 절차는 `MODELS.md`에 문서화.
- **네트워크 없음**: 1단계 스캐폴드는 네트워크 호출을 포함하지 않는다(모델은 수동 사이드로드). `INTERNET` 권한도 아직 추가하지 않는다.

## 6. 측정 계측

- `Benchmark.measure { }`로 벽시계 지연시간과 실행 전후 `Debug.MemoryInfo`의 PSS를 기록한다.
- 각 실행 결과를 `filesDir/bench_results.csv`에 append (model, inputId, latencyMs, peakRssKb, ok, note).
- Logcat 태그 `BENCH`로도 출력하여 즉시 확인 가능.
- `MODELS.md`에 10개 녹음 측정 기록 템플릿과 "런타임·포맷 확정 판단표"를 둔다.

## 7. 빌드 설정 변경

- `gradle/libs.versions.toml`에 `whisper-android`, `litertlm-android`, `kotlinx-coroutines`, `lifecycle-viewmodel-compose`, `navigation`(선택) 버전/라이브러리 추가.
- `app/build.gradle.kts`에 의존성 추가 + `ndk { abiFilters += "arm64-v8a" }`.
- `minSdk`: 현재 26 유지하되, LiteRT-LM/AAR 요구사항 확인 후 필요 시 상향(문서에 플래그).
- `INTERNET`/`RECORD_AUDIO` 권한은 1단계 스캐폴드에는 불필요(녹음 UI 없음, 수동 WAV 사용) → 추가하지 않음.

## 8. 이번 세션에서 확정 불가 (검증 책임 분담)

다음은 실기기·SDK·모델 파일이 있어야 가능하므로 사용자가 수행한다. 설계는 이를 가능케 하는 데까지만 책임진다.

- 실제 Gradle 빌드 통과(의존성 버전 해석, AAR/LiteRT-LM 링크)
- 모델 사이드로드 후 Whisper/Gemma 실행
- 속도·피크 메모리·한국어 품질 수치 수집(녹음 10개)
- 위 결과로 런타임·포맷 **최종 확정** 및 스펙 반영

## 9. 성공 기준

1. `bench/` 스캐폴드가 설계대로 존재하고, 모델 파일만 있으면 실기기에서 전사·분석을 각각 실행하도록 배선되어 있다.
2. 각 실행이 지연시간·피크 메모리를 CSV/Logcat으로 남긴다.
3. 분석 경로가 §3.4 고정 스키마 JSON을 파싱하고 실패 시 1회 재시도한다.
4. 두 모델이 동시에 메모리에 올라가지 않는다(코드상 보장).
5. `MODELS.md`만 따라 하면 모델 획득→사이드로드→측정이 가능하다.

## 10. 리스크 / 열린 항목

- whisper-android AAR의 한국어 전사 품질·안정성 미검증 → 부족하면 스펙 §2.2의 whisper.cpp 직접 JNI 빌드로 전환(인터페이스 뒤에 숨겨 전환 비용 최소화).
- LiteRT-LM Android 아티팩트 버전/최소 SDK가 유동적 → 빌드 시 버전 핀 확정 필요.
- 메모리 요구량(~8GB RAM)이 저사양 기기를 배제 → 제품 타깃 기기 정책 재검토 필요(후속).
- 이 프로젝트는 git 저장소가 아니므로 본 문서는 파일로만 보관(커밋 생략).
