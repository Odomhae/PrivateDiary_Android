# 모델 준비 · 측정 가이드 (1단계 기술 검증)

모델 파일은 저장소에 커밋하지 않는다. 아래 절차로 기기에 직접 넣는다. 앱 패키지는 `com.odom.privatediary`.

## 1. 모델 내려받기
- **Whisper Small ggml**: whisper.cpp 모델 저장소에서 `ggml-small.bin` (약 466MB~). 파일명을 `ggml-small.bin`으로 둔다.
- **Gemma 3n E2B (LiteRT-LM)**: HuggingFace `google/gemma-3n-E2B-it-litert-lm`
  (약 3.7GB, 실행 시 RAM 약 8GB). 받은 `.litertlm` 파일명을 `gemma-3n-E2B-it.litertlm`으로 둔다.

> 코드의 모델 경로(`ModelPaths`)는 `files/models/ggml-small.bin`, `files/models/gemma-3n-E2B-it.litertlm`을 기대한다. 파일명이 다르면 맞춰 바꾸거나 `ModelPaths.kt`를 수정한다.

## 2. 기기로 push (앱 1회 설치 후)
```bash
adb shell run-as com.odom.privatediary mkdir -p files/models files/samples
# 모델
adb push ggml-small.bin /data/local/tmp/
adb push gemma-3n-E2B-it.litertlm /data/local/tmp/
adb shell "run-as com.odom.privatediary cp /data/local/tmp/ggml-small.bin files/models/"
adb shell "run-as com.odom.privatediary cp /data/local/tmp/gemma-3n-E2B-it.litertlm files/models/"
# 한국어 녹음 10개 (WAV/MP3/FLAC 가능, 자동 16kHz 리샘플). sample01.wav ~ sample10.wav
adb push sample01.wav /data/local/tmp/
adb shell "run-as com.odom.privatediary cp /data/local/tmp/sample01.wav files/samples/"
# ... 나머지 샘플 반복
```

> `BenchScreen`은 현재 `files/samples/sample01.wav`를 사용한다. 녹음별 측정은 파일을 `sample01.wav`로 교체하며 반복하거나 `BenchScreen.kt`의 파일명을 바꾼다.

## 3. 측정
1. 앱 실행 → 모델 "준비됨" 표시 확인.
2. [Whisper 실행] → 전사문 확인 → [Gemma 실행] → 분석 JSON(ok 여부) 확인.
3. 결과는 `files/bench_results.csv`에 누적. 회수:
   ```bash
   adb shell run-as com.odom.privatediary cat files/bench_results.csv
   ```
   화면 하단 로그에도 모델별 지연시간·PSS가 표시된다.

> **측정값 해석 주의**
> - `pssKb`는 **추론 직후 1회 측정한 PSS(KB)** 로, 진짜 피크가 아니라 말단 샘플이다 — 실제 피크보다 **낮게** 나올 수 있다. Gemma 3n E2B가 기기 RAM(~8GB)에 맞는지 판단할 때는 이 값을 **하한**으로 보고 여유를 둬야 한다.
> - 화면에 `whisper ERROR ...` / `gemma ERROR ...` 로그가 뜨면 그 샘플은 **CSV에 행이 기록되지 않는다**(생성/엔진 예외는 재시도하지 않음). ERROR가 보이면 해당 녹음은 실패로 간주하고 원인을 따로 적는다.

## 4. 측정 기록 (녹음 10개)
| rec | whisper ms | whisper pssKb | 전사 품질(1~5) | gemma ms | gemma pssKb | JSON ok | 감정 적절(1~5) |
|-----|-----------|----------------|----------------|----------|--------------|---------|----------------|
| 01  |           |                |                |          |              |         |                |
| 02  |           |                |                |          |              |         |                |
| ... |           |                |                |          |              |         |                |
| 10  |           |                |                |          |              |         |                |

## 5. 런타임·포맷 확정 판단 (1단계 산출물)
- [ ] whisper-android AAR의 한국어 전사 품질이 쓸만한가? (아니면 스펙 §2.2의 whisper.cpp 직접 JNI 빌드로 전환 — `Transcriber` 인터페이스 뒤라 교체 비용 낮음)
- [ ] whisper-android AAR(커뮤니티 아티팩트)의 안정성·무결성 확인 (오프라인 동작이므로 프라이버시 원칙엔 부합)
- [ ] Gemma 3n E2B가 대상 기기에서 메모리·속도 허용 범위인가? (파일 ~3.7GB / RAM ~8GB — 스펙의 2GB/6GB 가정을 상향해야 함)
- [ ] `conversation.sendMessage(...).toString()`이 실제 생성 텍스트 전체를 주는지 확인 (아니면 `LiteRtDiaryAnalyzer`의 텍스트 추출을 0.17.x 실제 접근자로 교정)
- [ ] 감정/causes 라벨 일관성 (같은 전사 반복 입력 시 흔들림)
- [ ] 최종 결정: LLM 런타임 = LiteRT-LM 유지 / 변경, 포맷 = `.litertlm`
- [ ] 위 결론을 `voice-diary-spec.md` §2.2/§2.5/§3에 반영

## 참고: 1단계에서 확정된 빌드 환경
- compileSdk 37, Kotlin 2.4.0 (litertlm 0.17.1이 Kotlin 2.4 stdlib를 요구 → 프로젝트 정렬)
- 의존성: `dev.ffmpegkit-maintained:whisper-android:1.0.0`, `com.google.ai.edge.litertlm:litertlm-android:0.17.1`
- ABI: arm64-v8a only
