# 1분 음성 일기 앱 — 개발 스펙

하루 1분 말로 털어놓으면 기기 안에서 전사·요약·감정 분석을 하고, 감정 추이를 보여주는 온디바이스 앱.

## 1. 핵심 원칙

- **완전 온디바이스**: 음성, 전사문, 분석 결과는 기기 밖으로 나가지 않는다. 자체 서버 없음.
- **네트워크 호출은 모델 파일 다운로드 한 가지뿐**이다. 분석·통계·로그 전송 없음.
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
| DI | Hilt | |
| 로컬 DB | Room | 전문 검색은 Room FTS4 |
| 설정 저장 | DataStore (Preferences) | |
| 녹음 | `AudioRecord` | 16kHz, mono, 16-bit PCM — Whisper 입력 형식에 맞춤 |
| VAD | Silero VAD (ONNX 또는 Android용 래퍼) | |
| 전사 런타임 | whisper.cpp + JNI (NDK, CMake) | |
| LLM 런타임 | Google의 Android용 온디바이스 LLM 런타임 (LiteRT-LM / MediaPipe LLM Inference) 우선 검토, 대안은 llama.cpp + JNI (GGUF) | **1단계 기술 검증에서 확정**. Gemma 4 E2B를 공식 지원하는 런타임과 모델 포맷을 먼저 확인 |
| 모델 다운로드 | WorkManager + OkHttp | 포그라운드 서비스 알림, Range 이어받기, 비과금 네트워크 제약 조건 |
| 앱 잠금 | AndroidX Biometric (`BiometricPrompt`) | 기기 PIN/패턴 폴백 허용 |
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

### 2.4 Android 관련 요구사항
- 권한: `RECORD_AUDIO`, `POST_NOTIFICATIONS`(다운로드 진행 알림), `INTERNET`(모델 다운로드 전용)
- ABI: `arm64-v8a`만 지원
- 모델 파일은 앱 내부 저장소(`filesDir`)에 저장. 외부 저장소 사용 금지
- 대용량 모델 로드 전 가용 메모리 확인(`ActivityManager.MemoryInfo`), 부족하면 안내
- 긴 추론 중 화면 회전·백그라운드 전환에도 작업이 유지되도록 ViewModel 범위 밖(Repository 또는 WorkManager)에서 실행
- `android:allowBackup`과 백업 규칙으로 일기 DB의 클라우드 자동 백업을 기본 제외

### 2.5 아직 미정

| 항목 | 비고 |
|---|---|
| minSdk | 제안: 26 이상. LLM 런타임 요구사항에 따라 조정 |
| 최소 지원 기기 | Gemma 4 E2B가 RAM 약 2GB를 쓰므로 기기 RAM 6GB 이상 권장 |
| 수익화 | |

## 3. 모델 구성

### 3.1 음성 → 텍스트: Whisper Small
- 약 600MB, 한국어 포함 다국어 지원
- 런타임: whisper.cpp를 NDK로 빌드하고 JNI로 호출 (모델 포맷 ggml)
- **VAD 필수**: Whisper는 무음 구간에서 없는 말을 지어내는 문제가 있음. Silero VAD 등으로 무음을 잘라낸 뒤 전사
- 대안: SenseVoice Small (더 빠르고 목소리에서 감정 태그 추출 가능, 한국어 정확도는 Whisper가 약간 우세)

### 3.2 요약 + 감정 분석: Gemma 4 E2B
- 실행 시 RAM 약 2GB
- 전사문을 입력받아 요약·감정·태그를 **JSON 한 번에** 출력 (3.4 스키마)
- 별도 감정 분류 모델은 두지 않음

### 3.3 모델 배포
- 저장소: **Cloudflare R2, Standard 스토리지** (Infrequent Access는 무료 한도 미적용이므로 사용 금지)
- 무료 한도: 저장 10GB/월, 다운로드 전송량 무료, 읽기 요청 1,000만 건/월
- 앱 첫 실행 시 다운로드, 앱 번들에는 포함하지 않음
- 구현 요구사항
  - 이어받기 지원 (Range 요청). 조각 크기는 크게 잡을 것 — 조각 하나가 요청 1건으로 과금됨
  - "Wi-Fi에서만 다운로드" 옵션, 기본값 켜짐
  - 다운로드 후 체크섬(SHA-256) 검증
  - 진행률 표시, 저장 공간 부족 시 안내
  - 모델 버전 관리 (버전 매니페스트 JSON을 R2에 함께 둠)

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
  → Gemma 4 E2B 분석 (JSON)
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
| 원문과 검색 | 전사 원문 저장, 전문 검색, 전사 오류 직접 수정 |
| 주간 회고 | 최근 7일 요약을 Gemma에 다시 넣어 한 문단 리포트 생성 |
| 앱 잠금 | 지문 / 얼굴 인식 / 기기 PIN (`BiometricPrompt`) |
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
- 전사문 전문 검색용 Room FTS4 테이블
- Google 자동 백업에는 기본 제외, 포함 여부는 사용자가 선택

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

1. **기술 검증**: 빈 Compose 프로젝트에 whisper.cpp JNI와 LLM 런타임을 붙여 실제 기기에서 Whisper Small과 Gemma 4 E2B를 각각 실행. 속도·메모리·한국어 품질 측정, LLM 런타임과 모델 포맷 확정. 실제 한국어 일기 녹음 10개로 테스트
2. **파이프라인**: 녹음 → VAD → 전사 → 분석 → JSON 파싱까지 연결
3. **저장과 화면**: DB, 홈, 결과, 캘린더, 상세
4. **모델 다운로드**: R2 연동, 이어받기, 체크섬, 온보딩
5. **주간 회고, 검색, 앱 잠금, 위기 대응**
6. **다듬기**: 저사양 기기 대응, 오류 처리, Play 스토어 제출 (데이터 보안 양식에 "수집 데이터 없음" 기재)

## 9. 개발 전 확인할 것

- **Gemma 4 라이선스**: Apache 2.0이라는 자료와 자체 약관이라는 자료가 섞여 있음. 모델 공식 페이지에서 상업적 이용 조건 확인
- **Gemma 4 E2B 모델 파일 크기와 양자화 버전**: 다운로드 용량과 R2 저장량 계산에 필요
- **Gemma 4 E2B 오디오 직접 입력**: E 시리즈는 오디오 입력을 지원한다고 알려져 있음. 1분 길이를 처리할 수 있다면 Whisper 없이 모델 하나로 줄일 수 있는지 검토. 단, 전사 원문 저장과 검색이 필요하므로 전사 품질이 Whisper보다 나쁘면 분리 유지
- **한국어 품질**: 공개 벤치마크는 대부분 영어 기준. 사투리, 웅얼거림, 혼잣말에서 직접 테스트
- **감정 판정 일관성**: 같은 전사문을 여러 번 넣어 라벨과 강도가 흔들리는지 확인하고 프롬프트 조정
- **R2 요금**: 가입 시 결제 수단 등록 필요 여부와 최신 무료 한도 재확인 (https://developers.cloudflare.com/r2/pricing/)
