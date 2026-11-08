# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Phases 1–5c of `voice-diary-spec.md` §8 are implemented (pipeline, Room data layer, screens, Play AI pack delivery, crisis handling + search, weekly report, app lock + settings). Phase 6 (polish/release) has not started, and **nothing has been verified on a real device yet** — including the phase-1 measurements in `MODELS.md` §4–5. Spec §8 has the status table and §8.1 lists the remaining work. **`voice-diary-spec.md`** (Korean) is the source of truth for scope; per-phase design specs and plans are in `docs/superpowers/specs/` and `docs/superpowers/plans/`. `MODELS.md` covers getting the models, side-loading for debug builds, and testing AI packs locally.

## What this app is

**1분 음성 일기** — a fully **on-device** voice diary. The user speaks for up to 60s; the device transcribes (Whisper Small, `whisper-android` AAR wrapping whisper.cpp), then summarizes + does emotion analysis (Gemma 3 1B IT int4 on LiteRT-LM 0.17.1), and shows emotion trends over time.

Non-negotiable constraints from the spec that shape every design decision:

- **Fully on-device.** Audio, transcripts, and analysis never leave the device. There is no backend.
- **The app makes no network calls.** Models ship as Play AI packs (`:whisper_pack`, `:gemma_pack`, fast-follow) downloaded by the Play Store; there is no INTERNET permission. No analytics, logging, or telemetry. AI packs are capped at 1.5GB each — this is why the LLM is Gemma 3 1B int4.
- **Zero inference cost** — all models run locally on the user's device.
- **arm64-v8a only.** Models live in internal storage (`filesDir`), never external storage.
- **Never load Whisper and Gemma into memory at the same time.** Every model job runs inside `ModelGate.shared` (a Mutex).
- Diary DB, recordings and settings are excluded from Google cloud backup (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`). Keep it that way.

## Build & test

Use the Gradle wrapper (`./gradlew` on Unix, `gradlew.bat` on Windows/PowerShell). Modules: `:app`, plus the AI pack modules `:whisper_pack` and `:gemma_pack` (assets only; model files are git-ignored).

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

Device notes:
- The app is **arm64-only**, so x86_64 emulators can't install it. Use a real arm64 device.
- `installDebug` doesn't deliver the AI packs. For debug builds, side-load the models into `files/models/` (`MODELS.md` §2). To test pack download, use `bundleDebug`/`bundleRelease` + bundletool `--local-testing`.

## Toolchain specifics

- **AGP `9.2.0-alpha08`, Kotlin `2.4.0`, KSP `2.3.12`, Compose BOM `2026.02.01`.** These are bleeding-edge/alpha. Kotlin 2.4 is required by litertlm 0.17.1's stdlib. Versions are centralized in `gradle/libs.versions.toml` (version catalog); add dependencies there and reference them via `libs.*`, not inline coordinates.
- `compileSdk` = 37, `targetSdk` = 36, `minSdk` = 26.
- Java/Kotlin target: **JVM 11**.
- `namespace` / `applicationId`: `com.odom.privatediary`.
- Key deps:
  - Room 2.8.5 (KSP), Navigation Compose, Coroutines, kotlinx-serialization
  - `play:ai-delivery` 0.2.0-beta01, `whisper-android` 1.0.0, `litertlm-android` 0.17.1, `android-vad` silero (jitpack)
  - biometric 1.1.0, datastore-preferences 1.2.1, fragment-ktx 1.9.1
- Not used: Hilt (singletons via `X.get(context)` + `AndroidViewModel`), NDK/CMake, OkHttp, WorkManager.
- Manifest declares only `RECORD_AUDIO`. Do not add `INTERNET`.

## Architecture

MVVM + unidirectional data flow (ViewModel + StateFlow, Repository layer). The spec's multi-module layout (spec §2.3) is not adopted yet. Everything lives in `:app`, split by package under `com.odom.privatediary`:

- `bench`: runtime wrappers behind the `Transcriber` / `DiaryAnalyzer` interfaces (`WhisperTranscriber`, `LiteRtDiaryAnalyzer`, `LiteRtTextGenerator`), plus the prompt, JSON schema/parsing, `ModelPaths`, and the phase-1 bench screen
- `pipeline`: `AudioRecorder` (16kHz mono 16-bit PCM), Silero VAD + `SilenceTrimmer`, `DiaryPipeline`, `ModelGate`, record screen
- `data`: Room `DiaryDatabase` (schema v1), DAOs, `DiaryRepository`, `LIKE` search
- `download`: AI pack state tracking (`DownloadViewModel`, `ModelPacks`) + onboarding
- `safety`: rule-based `CrisisDetector`
- `report`: weekly report (`WeeklyReportService` runs in an app-wide scope)
- `lock`: `LockPolicy` (pure logic) + `AppLock` + `BiometricPrompt` + lock overlay
- `settings`: DataStore
- `ui`: screens and `AppNavHost`. `MainActivity` is a `FragmentActivity`, because `BiometricPrompt` requires it.

Keep the runtimes behind `Transcriber` / `DiaryAnalyzer` so the runtime or model can be swapped. Long inference must run **outside ViewModel scope** so it survives rotation and backgrounding. The weekly report does this. **The record pipeline still runs in `viewModelScope` and needs to move.** Put pure logic in plain classes/objects with JVM tests (TDD), and keep Android-bound code thin.

## Processing pipeline (spec §4)

```
record (≤60s) → VAD trims silence → Whisper transcribe
  → rule-based crisis-keyword check (§5.3) → Gemma analysis (JSON) → Room → result screen
```

- The LLM returns a **fixed JSON schema** (summary / emotion / intensity / causes / keywords / reply / crisis — see spec §3.4). `emotion` and `causes` use fixed label sets, not free text. On JSON parse failure: retry once, else store transcript only and mark analysis `failed`.
- **Crisis handling (§5.3):** rule-based keyword check is primary, LLM `crisis` is secondary. On detection, show a **fixed message** + counseling contact (자살예방 상담전화 **109**) instead of the LLM `reply`. The app never diagnoses or counsels.

## Data model

Room entities `Entry` and `WeeklyReport` are specified in `voice-diary-spec.md` §6 (note `causes`/`keywords` stored as JSON arrays in TEXT columns; `analysis_status` ∈ pending/done/failed). Search uses `LIKE` over transcript + summary. Phase 5a chose this over FTS4 to keep schema v1.

## Before implementing model/runtime work

Spec §9 lists the open questions: Gemma license terms, Korean-language quality (the main risk with a 1B model), and emotion-label consistency. Model files are `ggml-small.bin` and `gemma3-1b-it-int4.litertlm` (`ModelPaths`). Each AI pack must stay ≤1.5GB. Record the outcome of phase-1 device measurements in `MODELS.md` before changing models or runtimes.
