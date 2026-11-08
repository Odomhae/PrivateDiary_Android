# 1분 음성 일기 앱 — 개발 스펙

하루 1분 말로 털어놓으면 기기 안에서 전사·요약·감정 분석을 하고, 감정 추이를 보여주는 온디바이스 앱.

## 1. 핵심 원칙

- **완전 온디바이스**: 음성, 전사문, 분석 결과는 기기 밖으로 나가지 않는다. 자체 서버 없음.
- **앱은 네트워크를 쓰지 않는다** (INTERNET 권한 없음). 모델 파일은 Play 스토어가 AI pack으로 받아 준다(3.3). 분석·통계·로그 전송 없음.
- **녹음은 최대 1분**. 짧게, 매일.
- **추론 비용 0원**: 모든 모델은 사용자 기기에서 실행한다.

## 2. 플랫폼과 기술 스택

### 2.1 확정
- **플랫폼**: Android 전용
- **언어**: Kotlin
- **UI**: Jetpack Compose (Material 3), 단일 Activity

### 2.2 권장 구성 (제안값, 변경 가능)

| 영역 | 선택 | 비고 |
|---|---|---|
| 아키텍처 | MVVM + 단방향 데이터 흐름 | ViewModel + StateFlow, Repository 계층 |
| 비동기 | Kotlin Coroutines + Flow | 추론은 `Dispatchers.Default`의 전용 디스패처에서 실행 |
| 화면 이동 | Navigation Compose | |
| DI | Hilt | **미도입** — 현재 싱글턴(`get(context)`) + `AndroidViewModel`. 규모가 커지면 도입 |
| 로컬 DB | Room 2.8.5 (KSP) | 검색은 FTS4 대신 `LIKE` 채택(5a 결정, 스키마 v1 유지) |
| 설정 저장 | DataStore (Preferences) 1.2.1 | 앱 잠금 설정. 손상 시 잠금 켜짐으로 복구 |
| 녹음 | `AudioRecord` | 16kHz, mono, 16-bit PCM — Whisper 입력 형식에 맞춤 |
| VAD | Silero VAD — `android-vad` silero 2.0.10 (jitpack) | |
| 전사 런타임 | **현재** `whisper-android` 1.0.0 AAR(whisper.cpp 래퍼) | 한국어 품질·안정성이 부족하면 whisper.cpp 직접 JNI 빌드(NDK, CMake)로 교체 — `Transcriber` 인터페이스 뒤 |
| LLM 런타임 | **LiteRT-LM 0.17.1** (`.litertlm`) | 1단계에서 채택. 대안은 llama.cpp + JNI (GGUF). 기기 실측 후 최종 확정 |
| 모델 배포 | **Play AI pack (fast-follow)** — `play:ai-delivery` 0.2.0-beta01 | 다운로드·이어받기·무결성은 Play가 담당(3.3). (이전 안 WorkManager + OkHttp + R2는 폐기) |
| 앱 잠금 | AndroidX Biometric 1.1.0 (`BiometricPrompt`) | `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` — 기기 PIN/패턴 폴백, API 26+ |
| 암호화 | Android Keystore, 필요 시 SQLCipher | |
| 위젯 | Jetpack Glance | 이후 버전 |
| 알림 | WorkManager 또는 AlarmManager | 이후 버전 |

### 2.3 모듈 구조 (제안)

```
app/                 -- Compose UI, 내비게이션, DI 설정
core/audio/          -- 녹음, VAD
core/stt/            -- whisper.cpp JNI 래퍼
core/llm/            -- Gemma 런타임 래퍼, 프롬프트, JSON 파싱
core/model/          -- 모델 다운로드, 버전 매니페스트, 체크섬
core/data/           -- Room, Repository, DataStore
feature/record/      -- 녹음과 결과 화면
feature/calendar/    -- 캘린더, 일기 상세
feature/report/      -- 주간 회고, 추이
feature/settings/    -- 설정, 잠금, 내보내기
```

- `core/stt`와 `core/llm`은 인터페이스(`Transcriber`, `DiaryAnalyzer`) 뒤에 숨겨서 런타임이나 모델을 교체할 수 있게 한다
- **현재 구현**: 단일 모듈 `:app` + AI pack 모듈 `:whisper_pack`, `:gemma_pack`. 패키지로 나눔 — `bench`(런타임·프롬프트·JSON), `pipeline`(녹음·VAD·파이프라인·`ModelGate`), `data`(Room), `download`(AI pack 상태·온보딩), `safety`(위기 검사), `report`(주간 회고), `lock`(앱 잠금), `settings`, `ui`(화면). 멀티모듈 분리는 필요해지면 진행

### 2.4 Android 관련 요구사항
- 권한: `RECORD_AUDIO`만 선언 (`USE_BIOMETRIC`은 biometric 라이브러리에서 병합). **`INTERNET`·`POST_NOTIFICATIONS` 없음** — 모델은 Play가 받음
- ABI: `arm64-v8a`만 지원
- 모델 파일은 앱 내부 저장소(`filesDir`)에 저장. 외부 저장소 사용 금지
- 대용량 모델 로드 전 가용 메모리 확인(`ActivityManager.MemoryInfo`), 부족하면 안내
- 긴 추론 중 화면 회전·백그라운드 전환에도 작업이 유지되도록 ViewModel 범위 밖(Repository 또는 WorkManager)에서 실행
  - 현황: 주간 회고는 앱 범위 서비스(`WeeklyReportService`)로 충족. **녹음 파이프라인은 아직 `viewModelScope`** — 6단계 전 이전 필요
- Whisper와 Gemma를 동시에 메모리에 올리지 않는다 — 모든 모델 작업은 `ModelGate.shared`(Mutex) 안에서 실행
- `android:allowBackup`과 백업 규칙으로 일기 DB의 클라우드 자동 백업을 기본 제외 — **적용됨**: `data_extraction_rules.xml`(API 31+)은 클라우드 백업에서 전 도메인 제외, 기기 간 직접 이전은 허용. `backup_rules.xml`(API ≤30)은 전부 제외

### 2.5 아직 미정

| 항목 | 비고 |
|---|---|
| minSdk | 제안: 26 이상. LLM 런타임 요구사항에 따라 조정 |
| 최소 지원 기기 | arm64 기기만. Gemma 3 1B int4 + Whisper Small의 실제 메모리 사용량은 1단계 실측 후 결정 |
| 수익화 | |

## 3. 모델 구성

### 3.1 음성 → 텍스트: Whisper Small
- 약 600MB, 한국어 포함 다국어 지원
- 런타임: whisper.cpp를 NDK로 빌드하고 JNI로 호출 (모델 포맷 ggml)
- **VAD 필수**: Whisper는 무음 구간에서 없는 말을 지어내는 문제가 있음. Silero VAD 등으로 무음을 잘라낸 뒤 전사
- 대안: SenseVoice Small (더 빠르고 목소리에서 감정 태그 추출 가능, 한국어 정확도는 Whisper가 약간 우세)

### 3.2 요약 + 감정 분석: Gemma 3 1B IT int4 (원래 안: Gemma 4 E2B)
- **변경(PAD 전환)**: AI pack 팩당 1.5GB 한도로 Gemma 4 E2B(.litertlm 2.0~2.6GB)는 단일 팩에 들어가지 않음 → 현재는 **Gemma 3 1B IT int4** (`gemma3-1b-it-int4.litertlm`, 약 584MB, Gemma 약관). 한국어 분석 품질 재측정 필요
- 실행 시 메모리 사용량은 기기 실측 필요 (Gemma 4 E2B 기준 약 2GB였음)
- 전사문을 입력받아 요약·감정·태그를 **JSON 한 번에** 출력 (3.4 스키마)
- 별도 감정 분류 모델은 두지 않음

### 3.3 모델 배포
- **Play Asset Delivery — AI pack (Play for On-device AI)**, 전달 방식 **fast-follow** (설치 직후 Play가 자동으로 받음)
  - 팩: `whisper_pack`(Whisper), `gemma_pack`(LLM). 앱 기본 APK에는 포함하지 않음
  - 한도: 팩당 압축 1.5GB, 앱 전체 4GB → LLM은 1.5GB 이하 모델만 사용 가능
  - 이어받기·무결성·버전 관리는 Play가 담당 (앱 업데이트 시 팩도 함께 갱신). 앱은 INTERNET 권한 없음
  - 앱 구현: 온보딩에서 팩 상태·진행률 표시, 미설치 시 `fetch`, Wi-Fi 대기/모바일 데이터는 Play 확인 대화상자, 저장 공간 부족 등 오류 안내
  - (이전 안: Cloudflare R2 직접 다운로드 — 폐기)

### 3.4 LLM 출력 스키마

```json
{
  "summary": "한 줄 요약 (40자 이내)",
  "emotion": "기쁨 | 평온 | 설렘 | 슬픔 | 불안 | 분노 | 피곤",
  "intensity": 1,
  "causes": ["일", "관계"],
  "keywords": ["이사", "면접"],
  "reply": "공감 한마디 (조언 없이 들은 내용을 되짚는 1~2문장)",
  "crisis": false
}
```

- `emotion`: 위 7개 중 하나로 **고정** (제안값, 변경 가능). 소형 모델은 라벨이 자유로우면 결과가 들쭉날쭉함
- `intensity`: 1~5 정수
- `causes`: 고정 카테고리 `일, 관계, 건강, 수면, 돈, 자기계발, 기타` 중 0~3개 (제안값)
- `keywords`: 자유 태그 최대 3개
- 프롬프트에 few-shot 예시 3~5개 포함
- JSON 파싱 실패 시 1회 재시도, 그래도 실패하면 전사문만 저장하고 분석은 "나중에 다시 시도"로 표시

## 4. 처리 파이프라인

```
녹음 (최대 60초)
  → VAD로 무음 제거
  → Whisper Small 전사
  → 위기 표현 키워드 검사 (규칙 기반, 5.3 참고)
  → Gemma 3 1B 분석 (JSON)
  → 로컬 DB 저장
  → 결과 화면 (요약, 감정, 공감 한마디)
```

- 두 모델을 동시에 메모리에 올리지 않는다. Whisper 해제 후 Gemma 로드
- 전사와 분석은 백그라운드에서 실행하고, 화면에는 단계별 진행 상태 표시

## 5. 기능

### 5.1 MVP

| 기능 | 설명 |
|---|---|
| 녹음 | 최대 60초. 50초부터 남은 시간 표시. "이어서 녹음"으로 같은 날 일기에 덧붙이기 |
| 분석 결과 | 한 줄 요약, 감정 라벨과 강도, 키워드 태그, 공감 한마디 |
| 감정 캘린더 | 월 단위 달력에 날짜별 감정 색 표시. 날짜를 누르면 그날 일기 |
| 원문과 검색 | 전사 원문 저장, 검색(전사문·요약 `LIKE`), 전사 오류 직접 수정 |
| 주간 회고 | 최근 7일 요약을 Gemma에 다시 넣어 한 문단 리포트 생성 |
| 앱 잠금 | 지문 / 얼굴 인식 / 기기 PIN (`BiometricPrompt`). 기본 꺼짐, 켤 때 인증 1회. 콜드 스타트·백그라운드 30초 이상 후 복귀 시 잠금 |
| 위기 표현 대응 | 5.3 참고 |
| 모델 다운로드 | 3.3 참고. 온보딩에 포함 |

### 5.2 이후 버전

- **감정 원인 패턴**: `causes`와 `intensity`를 집계해 "수면 얘기한 날은 기분이 낮은 편" 같은 관찰 표시
- **오늘의 질문**: 최근 일기 기반으로 말 꺼낼 질문 생성
- **1년 전 오늘**
- **습관 형성**: 저녁 알림, 연속 기록 스트릭, 홈 화면 위젯에서 바로 녹음
- **음성 원본 보관 선택**: 전사 후 자동 삭제 / 보관
- **내보내기와 백업**: 암호화 파일로 내보내기와 가져오기 (서버가 없어 기기 변경 시 필수)
- **월간 리포트**

### 5.3 위기 표현 대응

- 전사문에서 자해·극단적 선택을 암시하는 표현을 **규칙 기반 키워드로 먼저** 검사하고, LLM의 `crisis` 값은 보조로 사용
- 감지되면 LLM이 생성한 `reply`를 보여주지 않고 **고정 문구**와 상담 연락처를 표시
  - 자살예방 상담전화 **109** (24시간)
- 일기 저장과 기록은 정상적으로 진행
- 앱은 상담이나 진단을 하지 않는다는 안내를 온보딩과 설정에 명시

## 6. 데이터 모델 (Room)

```
Entry
  id            TEXT PK
  date          TEXT    -- YYYY-MM-DD, 같은 날 여러 개 가능
  created_at    INTEGER
  duration_sec  INTEGER
  audio_path    TEXT NULL   -- 보관 옵션이 꺼져 있으면 NULL
  transcript    TEXT
  summary       TEXT NULL
  emotion       TEXT NULL
  intensity     INTEGER NULL
  causes        TEXT NULL   -- JSON 배열
  keywords      TEXT NULL   -- JSON 배열
  reply         TEXT NULL
  crisis        INTEGER DEFAULT 0
  analysis_status TEXT      -- pending | done | failed

WeeklyReport
  id          TEXT PK
  week_start  TEXT
  body        TEXT
  created_at  INTEGER
```

- DB와 음성 파일은 앱 내부 저장소에 보관
- ~~전사문 전문 검색용 Room FTS4 테이블~~ → 5a에서 `LIKE` 검색으로 결정(수천 건 규모에서 충분, 스키마 v1 유지)
- Google 자동 백업에는 기본 제외(적용됨), 포함 여부를 사용자가 고르는 옵션은 미구현

## 7. 화면

1. **온보딩**: 앱 소개 → 프라이버시 안내 → 모델 다운로드 → 마이크 권한
2. **홈**: 큰 녹음 버튼, 오늘 기록 여부, 최근 일기
3. **녹음**: 타이머, 파형, 정지
4. **결과**: 요약, 감정, 태그, 공감 한마디, 원문 펼치기
5. **캘린더**: 월별 감정 색, 날짜 선택
6. **일기 상세**: 원문 수정, 재분석, 삭제
7. **리포트**: 주간 회고, 감정 추이 그래프
8. **설정**: 잠금, 알림, 음성 보관, 모델 관리, 내보내기, 전체 삭제

## 8. 개발 단계

| 단계 | 내용 | 상태 (2026-11-09) |
|---|---|---|
| 1. 기술 검증 | Whisper·LLM 런타임을 붙여 실제 기기에서 속도·메모리·한국어 품질 측정, 런타임·포맷 확정. 한국어 일기 녹음 10개 | 코드 완료(`bench/`), **기기 실측 미완** (`MODELS.md` §4·§5 비어 있음) |
| 2. 파이프라인 | 녹음 → VAD → 전사 → 분석 → JSON 파싱 | 완료 |
| 3. 저장과 화면 | DB, 홈, 결과, 캘린더, 상세 | 완료 |
| 4. 모델 배포 | ~~R2 연동, 이어받기, 체크섬~~ → Play AI pack(fast-follow), 온보딩 | 완료. 팩 다운로드 흐름은 기기 미확인 |
| 5a. 위기 대응·검색 | 규칙 기반 위기 검사, 109 안내, 검색, 전사 수정 | 완료 |
| 5b. 주간 회고 | 월~일 주 회고, 감정 추이 | 완료 |
| 5c. 앱 잠금 | `BiometricPrompt`, 설정 화면(잠금·안내) | 완료. 기기 미확인 |
| 6. 다듬기 | 저사양 기기 대응, 오류 처리, Play 스토어 제출 (데이터 보안 양식에 "수집 데이터 없음" 기재) | 미착수 |

### 8.1 남은 작업
- **기기 확인**: 1단계 실측(Gemma 3 1B 한국어 분석 품질이 핵심), 앱 잠금, 위기 대응, 검색, bundletool `--local-testing`으로 팩 다운로드 흐름
- **MVP 미구현**
  - 녹음 파이프라인을 ViewModel 범위 밖으로 이전 (현재 회전·이탈 시 끊길 수 있음)
  - "이어서 녹음" — 현재는 녹음마다 별도 일기 (같은 날 여러 개, 횟수 제한 없음)
  - 50초부터 남은 시간 표시
  - 상세 화면 재분석 버튼 (현재 원문 수정·삭제만)
  - 모델 로드 전 가용 메모리 확인(`ActivityManager.MemoryInfo`)
  - 온보딩의 앱 소개·마이크 권한 단계 (현재 안내 문구 + 모델 다운로드만)
  - 백업 포함 여부 사용자 선택
- **설정 화면 나머지**: 알림, 음성 보관, 모델 관리, 내보내기, 전체 삭제
- **결정 필요**: 최근 앱 미리보기 가리기 — `FLAG_SECURE`(스크린샷도 차단) vs `setRecentsScreenshotEnabled(false)`(API 33+)
- **출시**: release 서명, AAB, Play Console 등록, 데이터 보안 양식

## 9. 개발 전 확인할 것

- **Gemma 라이선스**: Gemma 3 1B는 Gemma 이용 약관(Hugging Face에서 동의 후 다운로드). 상업적 이용 조건을 공식 페이지에서 확인
- ~~Gemma 4 E2B 모델 파일 크기와 양자화 버전~~ → AI pack 팩당 1.5GB 한도 때문에 Gemma 3 1B IT int4(약 584MB)로 결정. 더 큰 모델은 파일 분할·재조립 필요
- **Gemma 오디오 직접 입력**: Gemma 3 1B는 텍스트 전용이라 해당 없음. Whisper 분리 유지
- **한국어 품질**: 공개 벤치마크는 대부분 영어 기준. 사투리, 웅얼거림, 혼잣말에서 직접 테스트 — 1B 모델이라 특히 중요
- **감정 판정 일관성**: 같은 전사문을 여러 번 넣어 라벨과 강도가 흔들리는지 확인하고 프롬프트 조정
- ~~R2 요금~~ → Play AI pack으로 전환해 해당 없음
