# 2단계 파이프라인 연결 — 설계 문서

- 작성일: 2026-10-03
- 대상: `voice-diary-spec.md` §8 개발 단계 2단계
- 선행: 1단계(기술 검증) 완료 — `Transcriber`/`DiaryAnalyzer`, 벤치 하네스 존재
- 상태: 승인됨

## 1. 목적

1단계의 런타임 래퍼를 **실제 처리 파이프라인**으로 연결한다: 마이크 녹음 → VAD 무음 제거 → Whisper 전사 → Gemma 분석 → JSON. 결과를 화면에 단계별로 보여준다.

## 2. 범위

### 포함
- `AudioRecord` 실제 녹음(16kHz mono 16-bit PCM, ≤60초) → WAV 저장
- Silero VAD(android-vad 라이브러리)로 무음 프레임 제거
- 녹음→VAD→전사→분석을 순차 오케스트레이션하는 `DiaryPipeline`
- 단계별 진행 상태 + 결과 화면, `RECORD_AUDIO` 런타임 권한

### 제외 (후속 단계)
- Room 저장/검색 (3단계)
- 모델 다운로드/R2 (4단계)
- 위기 표현 대응, 앱 잠금, 주간 회고 (5단계)
- "이어서 녹음" 등 UX 디테일

## 3. 확정 사실 (사전 조사)

| 항목 | 결론 |
|---|---|
| VAD | `com.github.gkonovalov.android-vad:silero:2.0.10` (JitPack). Silero VAD ONNX(~2MB) **내장** — 모델 다운로드 불필요 |
| VAD API | `VadSilero(context, SampleRate.SAMPLE_RATE_16K, FrameSize.FRAME_SIZE_512, Mode.NORMAL, silenceDurationMs=300, speechDurationMs=50)`, `isSpeech(ByteArray): Boolean`, `AutoCloseable` |
| 16kHz 프레임 | 512 / 1024 / 1536 sample 허용. 512 사용(= 1024 byte, 16-bit) |
| 녹음 | `AudioRecord`, 16kHz, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT` |

## 4. 구조 (단일 모듈, 패키지 `com.odom.privatediary.pipeline`)

```
pipeline/
  WavWriter.kt          -- (순수) PCM16 mono 16k → WAV(RIFF) 바이트/파일
  AudioRecorder.kt      -- (스캐폴드) AudioRecord ≤60s → WavWriter → filesDir/recordings/*.wav
  VoiceActivityDetector.kt -- interface: isSpeech(frame: ByteArray): Boolean, release()
  SileroVad.kt          -- (스캐폴드) android-vad VadSilero 구현
  SilenceTrimmer.kt     -- (순수) PCM을 프레임으로 쪼개 VAD로 발화만 남김
  PipelineStage.kt      -- enum Idle/Recording/Trimming/Transcribing/Analyzing/Done/Failed
  DiaryPipeline.kt      -- (순수 오케스트레이션) trim→transcribe→analyze, 상태 방출, AnalysisResult
  PipelineViewModel.kt  -- (스캐폴드) 녹음/파이프라인 구동, StateFlow
  PipelineScreen.kt     -- (스캐폴드) 녹음 버튼, 단계 진행, 결과 표시
```

### 경계와 재사용
- 전사/분석은 1단계 `Transcriber`/`DiaryAnalyzer`를 그대로 소비(재구현 없음).
- VAD는 `VoiceActivityDetector` 인터페이스 뒤에 숨겨 교체 가능.
- `DiaryPipeline`은 `VoiceActivityDetector`, `Transcriber`, `DiaryAnalyzer`를 주입받아 orchestration만 담당 → fake로 완전 단위 테스트 가능.

### 데이터 흐름
```
PipelineScreen(녹음 버튼) → PipelineViewModel.record()
  → AudioRecorder: PCM 수집(≤60s) → WAV 저장 (stage=Recording)
  → SilenceTrimmer + SileroVad: 무음 제거 (stage=Trimming)  [VAD는 작으니 Whisper 로드 전 해제]
  → Transcriber(Whisper): 전사 (stage=Transcribing)  [전사 후 Whisper 해제]
  → DiaryAnalyzer(Gemma): 분석+JSON 파싱 (stage=Analyzing)
  → AnalysisResult 표시 (stage=Done) | 예외 시 stage=Failed
```

## 5. 핵심 제약 반영

- **Whisper·Gemma 동시 적재 금지**: 파이프라인은 각 모델을 쓰는 순간에만 로드하고 즉시 해제(1단계 불변식 유지). VAD(~2MB)는 전사 전에 끝내고 해제.
- **arm64-v8a only** 유지. **네트워크 없음**(INTERNET 미추가; android-vad 모델은 번들).
- 녹음·WAV는 앱 내부 저장소(`filesDir/recordings`).
- 녹음 최대 60초(스펙 §5.1).

## 6. 테스트 전략

### 순수 로직 (JVM 단위 테스트, 기기 불필요)
- `WavWriter`: 44바이트 RIFF 헤더 정확성(ChunkID "RIFF"/"WAVE", fmt 16/PCM/채널1/16kHz/bitsPerSample16, data 크기), 샘플 바이트 보존.
- `SilenceTrimmer`: fake VAD로 프레임 분할·발화 프레임 결합, 전무음→빈/최소, 혼합, 프레임 미만 꼬리 처리.
- `DiaryPipeline`: fake VAD/Transcriber/Analyzer로 happy 경로(최종 AnalysisResult), 단계 전이 순서, 전사/분석 예외 시 Failed, trim 결과가 Transcriber로 전달되는지.

### 스캐폴드 (컴파일 + 기기 실행은 사용자)
`AudioRecorder`, `SileroVad`, `PipelineViewModel`, `PipelineScreen`.

## 7. 빌드 변경
- `settings.gradle.kts` `dependencyResolutionManagement.repositories`에 `maven { url = uri("https://jitpack.io") }` 추가.
- 카탈로그: `android-vad-silero = "2.0.10"` + 라이브러리 `com.github.gkonovalov.android-vad:silero`.
- `app/build.gradle.kts`: `implementation(libs.android.vad.silero)`.
- `AndroidManifest.xml`: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`.

## 8. 성공 기준
1. `DiaryPipeline`이 주입된 VAD/Transcriber/Analyzer로 trim→transcribe→analyze를 순서대로 돌리고 `AnalysisResult`를 낸다(단위 테스트로 증명).
2. `WavWriter`/`SilenceTrimmer` 순수 로직이 테스트로 검증된다.
3. 스캐폴드가 실제 android-vad + AudioRecord API와 컴파일된다.
4. 두 대형 모델이 동시에 메모리에 올라가지 않는다(코드상 보장).
5. 녹음 버튼 하나로 기기에서 end-to-end가 돌 수 있도록 배선된다(기기 검증은 사용자).

## 9. 리스크 / 열린 항목
- android-vad의 정확한 패키지/멤버명은 설치 후 IDE에서 확정(컴파일로 검증).
- VAD 프레임 크기(512 sample=1024 byte)와 AudioRecord 버퍼 정렬 — 프레임 경계 처리 주의.
- 긴 추론 중 화면 회전/백그라운드 — 스펙은 ViewModel 범위 밖(Repository/WorkManager) 권고. Phase 2 스파이크는 ViewModel 스코프로 두고, 견고한 수명주기는 3단계 이후로 미룸(문서에 명시).
- Gemma 오디오 직접 입력으로 Whisper 생략 가능성(스펙 §9)은 1단계 측정 결과에 따라 후속 검토 — Phase 2는 Whisper 유지.
