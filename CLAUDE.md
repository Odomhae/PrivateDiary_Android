# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is a **fresh Android Studio Compose template** (just `MainActivity` + `ui/theme/`). Almost nothing from the product spec is implemented yet. The real design lives in **`voice-diary-spec.md`** (Korean) — treat it as the source of truth for architecture, data model, and feature scope. Read it before building any feature.

## What this app is

**1분 음성 일기** — a fully **on-device** voice diary. The user speaks for up to 60s; the device transcribes (Whisper Small via whisper.cpp/JNI), then summarizes + does emotion analysis (Gemma on an on-device LLM runtime), and shows emotion trends over time.

Non-negotiable constraints from the spec that shape every design decision:

- **Fully on-device.** Audio, transcripts, and analysis never leave the device. There is no backend.
- **The only network call is model-file download** (first run, from Cloudflare R2). No analytics, logging, or telemetry.
- **Zero inference cost** — all models run locally on the user's device.
- **arm64-v8a only.** Models live in internal storage (`filesDir`), never external storage.
- **Never load Whisper and Gemma into memory at the same time** — release one before loading the other (see pipeline in spec §4).
- Diary DB is excluded from Google cloud auto-backup by default.

## Build & test

Use the Gradle wrapper (`./gradlew` on Unix, `gradlew.bat` on Windows/PowerShell). Single-module project; the module is `:app`.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # instrumented tests on device (app/src/androidTest)
./gradlew lint                   # Android Lint
```

Run a single unit test class or method:

```bash
./gradlew test --tests "com.odom.privatediary.ExampleUnitTest"
./gradlew test --tests "com.odom.privatediary.ExampleUnitTest.someMethod"
```

## Toolchain specifics

- **AGP `9.2.0-alpha08`, Kotlin `2.2.10`, Compose BOM `2026.02.01`** — these are bleeding-edge/alpha. Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog); add dependencies there and reference via `libs.*`, not inline coordinates.
- `compileSdk`/`targetSdk` = 36, `minSdk` = 26 (may rise per LLM-runtime requirements — see spec §2.5).
- Java/Kotlin target: **JVM 11**.
- `namespace` / `applicationId`: `com.odom.privatediary`.
- The project does **not** yet declare Hilt, Room, Navigation, Coroutines, NDK/CMake, or the STT/LLM runtimes — these are planned (spec §2.2) and must be added as features are built.

## Architecture to build toward (from the spec)

MVVM + unidirectional data flow (ViewModel + StateFlow, Repository layer). The spec proposes a multi-module layout — the repo is currently single-module `:app`, so introduce modules as the work reaches each layer:

- `core/audio` — recording (`AudioRecord`, 16kHz mono 16-bit PCM) + VAD (Silero)
- `core/stt` — whisper.cpp JNI wrapper behind a `Transcriber` interface
- `core/llm` — Gemma runtime wrapper behind a `DiaryAnalyzer` interface, prompts, JSON parsing
- `core/model` — model download (WorkManager + OkHttp), version manifest, SHA-256 checksum, resumable Range requests
- `core/data` — Room (+ FTS4 full-text search), Repository, DataStore
- `feature/record`, `feature/calendar`, `feature/report`, `feature/settings`

Keep `core/stt` and `core/llm` behind the `Transcriber` / `DiaryAnalyzer` interfaces so the runtime or model can be swapped. Long inference must run **outside ViewModel scope** (Repository or WorkManager) so it survives rotation/backgrounding.

## Processing pipeline (spec §4)

```
record (≤60s) → VAD trims silence → Whisper transcribe
  → rule-based crisis-keyword check (§5.3) → Gemma analysis (JSON) → Room → result screen
```

- The LLM returns a **fixed JSON schema** (summary / emotion / intensity / causes / keywords / reply / crisis — see spec §3.4). `emotion` and `causes` use fixed label sets, not free text. On JSON parse failure: retry once, else store transcript only and mark analysis `failed`.
- **Crisis handling (§5.3):** rule-based keyword check is primary, LLM `crisis` is secondary. On detection, show a **fixed message** + counseling contact (자살예방 상담전화 **109**) instead of the LLM `reply`. The app never diagnoses or counsels.

## Data model

Room entities `Entry` and `WeeklyReport` are specified in `voice-diary-spec.md` §6 (note `causes`/`keywords` stored as JSON arrays in TEXT columns; `analysis_status` ∈ pending/done/failed). A separate FTS4 table backs transcript search.

## Before implementing model/runtime work

Spec §9 lists open questions that must be resolved first (Gemma license terms, model file size/quantization, whether Gemma's audio input can replace Whisper, Korean-language quality, emotion-label consistency, R2 free-tier limits). Don't hard-code model choices without checking these.
