# 2단계 파이프라인 연결 Implementation Plan

> **For agentic workers:** executed inline by the controller (this environment's background agents cannot run Gradle). Pure-logic tasks are TDD via `./gradlew :app:testDebugUnitTest`; scaffold tasks verified via `./gradlew :app:compileDebugKotlin`. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 1단계 런타임을 실제 파이프라인(녹음 → VAD 무음 제거 → 전사 → 분석 → JSON)으로 연결한다.

**Architecture:** 순수 로직(WAV 인코딩, 무음 트리밍, 파이프라인 오케스트레이션)은 인터페이스 뒤에서 fake로 JVM 테스트. 기기 결합부(AudioRecord, Silero VAD, ViewModel, Compose)는 실제 API로 스캐폴드. 전사/분석은 1단계 `Transcriber`/`DiaryAnalyzer`를 재사용하되 인터페이스에 `suspend fun load()`를 추가해 파이프라인이 로드→사용→해제를 순차 제어한다.

**Tech Stack:** Kotlin 2.4.0, Compose, Coroutines, `com.github.gkonovalov.android-vad:silero:2.0.10` (JitPack), `AudioRecord`.

## Global Constraints
- 패키지 루트 `com.odom.privatediary.pipeline`. 단일 모듈 `:app`.
- Whisper·Gemma 동시 적재 금지: 파이프라인은 각 모델을 load→use→release 순차 처리. VAD(~2MB)는 전사 전에 끝내고 release.
- arm64-v8a only. INTERNET 권한 미추가(android-vad 모델 번들). `RECORD_AUDIO`는 이 단계에서 추가.
- 녹음: 16kHz, mono, 16-bit PCM, 최대 60초. WAV는 `filesDir/recordings`.
- VAD 프레임: 512 sample = 1024 byte.
- 커밋 trailer: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- 검증은 컨트롤러가 메인 세션에서 Gradle 실행; 기기 end-to-end는 사용자.

---

### Task 1: 빌드 설정 — JitPack, android-vad, RECORD_AUDIO

**Files:** Modify `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`

- [ ] settings.gradle.kts `dependencyResolutionManagement { repositories { ... } }`에 추가: `maven { url = uri("https://jitpack.io") }`
- [ ] libs.versions.toml: `[versions]`에 `androidVadSilero = "2.0.10"`; `[libraries]`에 `android-vad-silero = { group = "com.github.gkonovalov.android-vad", name = "silero", version.ref = "androidVadSilero" }`
- [ ] app/build.gradle.kts dependencies에 `implementation(libs.android.vad.silero)`
- [ ] AndroidManifest.xml `<manifest>` 안, `<application>` 앞에 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
- [ ] 검증: `./gradlew :app:dependencies --configuration debugRuntimeClasspath` → `com.github.gkonovalov.android-vad:silero:2.0.10` 해석 확인
- [ ] Commit: `build: add android-vad (silero) via jitpack + RECORD_AUDIO permission`

---

### Task 2: WavWriter (순수/TDD)

**Files:** Create `app/src/main/java/com/odom/privatediary/pipeline/WavWriter.kt`, Test `app/src/test/java/com/odom/privatediary/pipeline/WavWriterTest.kt`

**Produces:** `WavWriter.wavBytes(pcm: ByteArray, sampleRate=16000, channels=1, bitsPerSample=16): ByteArray`; `WavWriter.writeWav(file: File, pcm: ByteArray, ...)`.

- [ ] 실패 테스트:
```kotlin
package com.odom.privatediary.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class WavWriterTest {
    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o+1].toInt() and 0xFF) shl 8) or
        ((b[o+2].toInt() and 0xFF) shl 16) or ((b[o+3].toInt() and 0xFF) shl 24)

    @Test fun header_and_sizes_are_correct() {
        val pcm = ByteArray(100) { it.toByte() }
        val w = WavWriter.wavBytes(pcm)
        assertEquals(144, w.size)                       // 44 header + 100 data
        assertEquals("RIFF", String(w, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 100, le32(w, 4))              // chunk size
        assertEquals("WAVE", String(w, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(w, 12, 4, Charsets.US_ASCII))
        assertEquals(16, le32(w, 16))                   // subchunk1 size
        assertEquals(1, le16(w, 20))                    // PCM
        assertEquals(1, le16(w, 22))                    // mono
        assertEquals(16000, le32(w, 24))               // sample rate
        assertEquals(16000 * 1 * 16 / 8, le32(w, 28))  // byte rate
        assertEquals(2, le16(w, 32))                    // block align
        assertEquals(16, le16(w, 34))                   // bits per sample
        assertEquals("data", String(w, 36, 4, Charsets.US_ASCII))
        assertEquals(100, le32(w, 40))                  // data size
    }

    @Test fun pcm_payload_is_appended_verbatim() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val w = WavWriter.wavBytes(pcm)
        assertEquals(1, w[44].toInt()); assertEquals(4, w[47].toInt())
    }
}
```
- [ ] 구현:
```kotlin
package com.odom.privatediary.pipeline

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun wavBytes(
        pcm: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)                       // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    fun writeWav(file: File, pcm: ByteArray, sampleRate: Int = 16000) {
        file.parentFile?.mkdirs()
        file.writeBytes(wavBytes(pcm, sampleRate))
    }
}
```
- [ ] 테스트 통과 확인 → Commit: `feat(pipeline): WAV writer for 16k mono PCM`

---

### Task 3: VoiceActivityDetector 인터페이스 + SilenceTrimmer (순수/TDD)

**Files:** Create `pipeline/VoiceActivityDetector.kt`, `pipeline/SilenceTrimmer.kt`; Test `pipeline/SilenceTrimmerTest.kt`

**Produces:** `interface VoiceActivityDetector { fun isSpeech(frame: ByteArray): Boolean; fun release() }`; `class SilenceTrimmer(frameBytes: Int = 1024) { fun trim(pcm: ByteArray, vad: VoiceActivityDetector): ByteArray }`.

- [ ] 실패 테스트 (fake VAD: 첫 바이트가 1이면 speech):
```kotlin
package com.odom.privatediary.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceTrimmerTest {
    private fun vadFirstByteOne() = object : VoiceActivityDetector {
        override fun isSpeech(frame: ByteArray) = frame.isNotEmpty() && frame[0].toInt() == 1
        override fun release() {}
    }

    @Test fun keeps_only_speech_frames() {
        val speech = ByteArray(4) { 1 }      // frame marked speech
        val silence = ByteArray(4) { 0 }     // frame marked silence
        val pcm = silence + speech + silence + speech
        val out = SilenceTrimmer(frameBytes = 4).trim(pcm, vadFirstByteOne())
        assertArrayEquals(speech + speech, out)
    }

    @Test fun all_silence_yields_empty() {
        val pcm = ByteArray(16) { 0 }
        assertEquals(0, SilenceTrimmer(frameBytes = 4).trim(pcm, vadFirstByteOne()).size)
    }

    @Test fun trailing_partial_frame_is_kept() {
        val speech = ByteArray(4) { 1 }
        val tail = byteArrayOf(1, 2)         // shorter than frame
        val out = SilenceTrimmer(frameBytes = 4).trim(speech + tail, vadFirstByteOne())
        assertArrayEquals(speech + tail, out)
    }
}
```
- [ ] 구현:
```kotlin
// VoiceActivityDetector.kt
package com.odom.privatediary.pipeline

interface VoiceActivityDetector {
    fun isSpeech(frame: ByteArray): Boolean
    fun release()
}
```
```kotlin
// SilenceTrimmer.kt
package com.odom.privatediary.pipeline

import java.io.ByteArrayOutputStream

/** PCM을 frameBytes 단위로 쪼개 VAD가 발화로 판정한 프레임만 남긴다.
 *  프레임보다 짧은 꼬리는 분류 불가라 그대로 보존한다(마지막 음성 잘림 방지). */
class SilenceTrimmer(private val frameBytes: Int = 1024) {
    fun trim(pcm: ByteArray, vad: VoiceActivityDetector): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i + frameBytes <= pcm.size) {
            val frame = pcm.copyOfRange(i, i + frameBytes)
            if (vad.isSpeech(frame)) out.write(frame)
            i += frameBytes
        }
        if (i < pcm.size) out.write(pcm, i, pcm.size - i)
        return out.toByteArray()
    }
}
```
- [ ] 테스트 통과 → Commit: `feat(pipeline): VAD interface and silence trimmer`

---

### Task 4: PipelineStage + DiaryPipeline + 인터페이스 load() 추가 (순수/TDD)

**Files:** Create `pipeline/PipelineStage.kt`, `pipeline/DiaryPipeline.kt`; Test `pipeline/DiaryPipelineTest.kt`; Modify `bench/Runtimes.kt` (add `suspend fun load()`), `bench/WhisperTranscriber.kt` & `bench/LiteRtDiaryAnalyzer.kt` (mark `load()` as `override`).

**Interfaces consumed:** `Transcriber`, `DiaryAnalyzer` (bench), `TranscriptResult`, `AnalysisResult`, `WavWriter`, `SilenceTrimmer`, `VoiceActivityDetector`.
**Produces:** `enum class PipelineStage { Idle, Recording, Trimming, Transcribing, Analyzing, Done, Failed }`; `class DiaryPipeline(vad, trimmer, transcriber, analyzer, onStage)` with `suspend fun run(pcm: ByteArray, workWav: File): AnalysisResult`.

- [ ] Modify `bench/Runtimes.kt` — add `suspend fun load()` to both interfaces:
```kotlin
interface Transcriber {
    suspend fun load()
    suspend fun transcribe(wav: File, language: String = "ko"): TranscriptResult
    fun release()
}
interface DiaryAnalyzer {
    suspend fun load()
    suspend fun analyze(transcript: String): AnalysisResult
    fun release()
}
```
- [ ] Modify `bench/WhisperTranscriber.kt`: change `suspend fun load()` → `override suspend fun load()`.
- [ ] Modify `bench/LiteRtDiaryAnalyzer.kt`: change `suspend fun load()` → `override suspend fun load()`.
- [ ] 실패 테스트 (fakes):
```kotlin
package com.odom.privatediary.pipeline

import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.bench.DiaryAnalyzer
import com.odom.privatediary.bench.Transcriber
import com.odom.privatediary.bench.TranscriptResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiaryPipelineTest {
    private val passVad = object : VoiceActivityDetector {
        override fun isSpeech(frame: ByteArray) = true
        override fun release() {}
    }
    private fun fakeTranscriber(text: String, calls: MutableList<String>) = object : Transcriber {
        override suspend fun load() { calls += "t.load" }
        override suspend fun transcribe(wav: File, language: String): TranscriptResult {
            calls += "t.transcribe"; return TranscriptResult(text, 1, 1)
        }
        override fun release() { calls += "t.release" }
    }
    private fun fakeAnalyzer(calls: MutableList<String>, throwIt: Boolean = false) = object : DiaryAnalyzer {
        override suspend fun load() { calls += "a.load" }
        override suspend fun analyze(transcript: String): AnalysisResult {
            calls += "a.analyze:$transcript"
            if (throwIt) throw RuntimeException("boom")
            return AnalysisResult("s", "평온", 2, emptyList(), emptyList(), "r", false, "{}", true)
        }
        override fun release() { calls += "a.release" }
    }

    @Test fun runs_stages_in_order_and_returns_result() = runTest {
        val stages = mutableListOf<PipelineStage>()
        val calls = mutableListOf<String>()
        val wav = File.createTempFile("p", ".wav")
        val pipeline = DiaryPipeline(passVad, SilenceTrimmer(4), fakeTranscriber("안녕", calls), fakeAnalyzer(calls)) { stages += it }
        val result = pipeline.run(ByteArray(8) { 1 }, wav)
        assertEquals("평온", result.emotion)
        assertEquals(listOf(PipelineStage.Trimming, PipelineStage.Transcribing, PipelineStage.Analyzing, PipelineStage.Done), stages)
        assertEquals(listOf("t.load", "t.transcribe", "t.release", "a.load", "a.analyze:안녕", "a.release"), calls)
    }

    @Test fun transcript_is_passed_to_analyzer() = runTest {
        val calls = mutableListOf<String>()
        val wav = File.createTempFile("p", ".wav")
        DiaryPipeline(passVad, SilenceTrimmer(4), fakeTranscriber("오늘 피곤", calls), fakeAnalyzer(calls)) {}
            .run(ByteArray(4) { 1 }, wav)
        assertTrue(calls.contains("a.analyze:오늘 피곤"))
    }

    @Test fun analyzer_failure_propagates_without_done() = runTest {
        val stages = mutableListOf<PipelineStage>()
        val calls = mutableListOf<String>()
        val wav = File.createTempFile("p", ".wav")
        val pipeline = DiaryPipeline(passVad, SilenceTrimmer(4), fakeTranscriber("x", calls), fakeAnalyzer(calls, throwIt = true)) { stages += it }
        var threw = false
        try { pipeline.run(ByteArray(4) { 1 }, wav) } catch (e: Exception) { threw = true }
        assertTrue(threw)
        assertTrue(!stages.contains(PipelineStage.Done))
    }
}
```
- [ ] 구현:
```kotlin
// PipelineStage.kt
package com.odom.privatediary.pipeline

enum class PipelineStage { Idle, Recording, Trimming, Transcribing, Analyzing, Done, Failed }
```
```kotlin
// DiaryPipeline.kt
package com.odom.privatediary.pipeline

import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.bench.DiaryAnalyzer
import com.odom.privatediary.bench.Transcriber
import java.io.File

/** 녹음 PCM을 받아 VAD 트리밍 → 전사 → 분석 순서로 처리한다.
 *  각 모델을 쓰는 순간에만 load/release 하여 Whisper·Gemma 동시 적재를 막는다. */
class DiaryPipeline(
    private val vad: VoiceActivityDetector,
    private val trimmer: SilenceTrimmer,
    private val transcriber: Transcriber,
    private val analyzer: DiaryAnalyzer,
    private val onStage: (PipelineStage) -> Unit = {},
) {
    suspend fun run(pcm: ByteArray, workWav: File): AnalysisResult {
        onStage(PipelineStage.Trimming)
        val trimmed = trimmer.trim(pcm, vad)
        vad.release()
        WavWriter.writeWav(workWav, trimmed)

        onStage(PipelineStage.Transcribing)
        transcriber.load()
        val transcript = transcriber.transcribe(workWav)
        transcriber.release()

        onStage(PipelineStage.Analyzing)
        analyzer.load()
        val result = analyzer.analyze(transcript.text)
        analyzer.release()

        onStage(PipelineStage.Done)
        return result
    }
}
```
- [ ] 테스트 통과 → Commit: `feat(pipeline): DiaryPipeline orchestration + load() on runtime interfaces`

---

### Task 5: AudioRecorder (스캐폴드)

**Files:** Create `pipeline/AudioRecorder.kt`

- [ ] 구현:
```kotlin
package com.odom.privatediary.pipeline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 16kHz mono 16-bit PCM 녹음. stop() 또는 maxMs 도달 시 종료. RECORD_AUDIO 권한 필요. */
class AudioRecorder(private val sampleRate: Int = 16000) {
    @Volatile private var recording = false

    @SuppressLint("MissingPermission")
    suspend fun record(maxMs: Long = 60_000): ByteArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf,
        )
        val out = ByteArrayOutputStream()
        val buf = ByteArray(minBuf)
        recorder.startRecording()
        recording = true
        val deadline = System.currentTimeMillis() + maxMs
        while (recording && System.currentTimeMillis() < deadline) {
            val n = recorder.read(buf, 0, buf.size)
            if (n > 0) out.write(buf, 0, n)
        }
        recorder.stop()
        recorder.release()
        out.toByteArray()
    }

    fun stop() { recording = false }
}
```
- [ ] `./gradlew :app:compileDebugKotlin` 통과 → Commit: `feat(pipeline): AudioRecorder (AudioRecord 16k mono PCM)`

---

### Task 6: SileroVad (스캐폴드, android-vad)

**Files:** Create `pipeline/SileroVad.kt`

- [ ] 구현:
```kotlin
package com.odom.privatediary.pipeline

import android.content.Context
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate

/** android-vad의 Silero VAD 래퍼. 프레임은 512 sample(=1024 byte, 16-bit)이어야 한다. */
class SileroVad(context: Context) : VoiceActivityDetector {
    private val vad = VadSilero(
        context,
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_512,
        mode = Mode.NORMAL,
        silenceDurationMs = 300,
        speechDurationMs = 50,
    )

    override fun isSpeech(frame: ByteArray): Boolean = vad.isSpeech(frame)
    override fun release() = vad.close()
}
```
> ⚠ `com.konovalov.vad.silero.*` import 경로는 설치된 라이브러리에서 IDE 자동완성으로 확정·교정한다. 메서드(`isSpeech(ByteArray)`, `close()`)와 빌더 파라미터는 README 기준.
- [ ] `./gradlew :app:compileDebugKotlin` 통과 → Commit: `feat(pipeline): SileroVad over android-vad`

---

### Task 7: PipelineViewModel (스캐폴드)

**Files:** Create `pipeline/PipelineViewModel.kt`

- [ ] 구현:
```kotlin
package com.odom.privatediary.pipeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.bench.LiteRtDiaryAnalyzer
import com.odom.privatediary.bench.ModelPaths
import com.odom.privatediary.bench.WhisperTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PipelineUiState(
    val stage: PipelineStage = PipelineStage.Idle,
    val transcript: String = "",
    val analysis: AnalysisResult? = null,
    val error: String? = null,
)

class PipelineViewModel(app: Application) : AndroidViewModel(app) {
    private val paths = ModelPaths.from(app)
    private val recorder = AudioRecorder()
    private val dispatcher = Dispatchers.Default

    private val _state = MutableStateFlow(PipelineUiState())
    val state: StateFlow<PipelineUiState> = _state.asStateFlow()

    val modelsReady: Boolean get() = paths.whisperReady() && paths.gemmaReady()

    fun start() {
        if (_state.value.stage != PipelineStage.Idle &&
            _state.value.stage != PipelineStage.Done &&
            _state.value.stage != PipelineStage.Failed
        ) return
        _state.value = PipelineUiState(stage = PipelineStage.Recording)
        viewModelScope.launch {
            try {
                val pcm = recorder.record()
                val app = getApplication<Application>()
                val pipeline = DiaryPipeline(
                    vad = SileroVad(app),
                    trimmer = SilenceTrimmer(frameBytes = 1024),
                    transcriber = WhisperTranscriber(app, paths.whisperModel.absolutePath),
                    analyzer = LiteRtDiaryAnalyzer(paths.gemmaModel.absolutePath, app.cacheDir.absolutePath),
                    onStage = { s -> _state.value = _state.value.copy(stage = s) },
                )
                val workWav = File(File(app.filesDir, "recordings"), "last.wav")
                val result = withContext(dispatcher) { pipeline.run(pcm, workWav) }
                _state.value = _state.value.copy(
                    stage = PipelineStage.Done, analysis = result, transcript = result.raw,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = PipelineStage.Failed, error = e.message)
            }
        }
    }

    fun stopRecording() = recorder.stop()
}
```
- [ ] `./gradlew :app:compileDebugKotlin` 통과 → Commit: `feat(pipeline): PipelineViewModel`

---

### Task 8: PipelineScreen + MainActivity + 권한 (스캐폴드)

**Files:** Create `pipeline/PipelineScreen.kt`; Modify `MainActivity.kt`

- [ ] `PipelineScreen.kt`:
```kotlin
package com.odom.privatediary.pipeline

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PipelineScreen(vm: PipelineViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.start() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("음성 일기")
        Text("모델: ${if (vm.modelsReady) "준비됨" else "없음 (adb push 필요)"}")
        Text("단계: ${state.stage}")
        Button(
            enabled = vm.modelsReady && (state.stage == PipelineStage.Idle ||
                state.stage == PipelineStage.Done || state.stage == PipelineStage.Failed),
            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        ) { Text("녹음 시작") }
        Button(
            enabled = state.stage == PipelineStage.Recording,
            onClick = { vm.stopRecording() },
        ) { Text("정지") }

        state.analysis?.let {
            Text("요약: ${it.summary}")
            Text("감정: ${it.emotion} (강도 ${it.intensity})")
            Text("태그: ${it.keywords.joinToString(", ")}")
            Text("공감: ${it.reply}")
        }
        state.error?.let { Text("오류: $it") }
    }
}
```
- [ ] `MainActivity.kt`를 PipelineScreen 호스팅으로 교체 (import를 `bench` → `pipeline`로):
```kotlin
package com.odom.privatediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odom.privatediary.pipeline.PipelineScreen
import com.odom.privatediary.pipeline.PipelineViewModel
import com.odom.privatediary.ui.theme.PrivateDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateDiaryTheme {
                val vm: PipelineViewModel = viewModel()
                PipelineScreen(vm)
            }
        }
    }
}
```
- [ ] `./gradlew :app:compileDebugKotlin` 통과 → Commit: `feat(pipeline): pipeline screen, permission flow, MainActivity host`

---

## Self-Review
- 스펙 커버리지: 녹음(T5), VAD(T3,T6), 트리밍(T3), 파이프라인 오케스트레이션(T4), WAV(T2), 권한(T1,T8), 화면(T8), 동시적재 금지(T4 load/release 순차), 빌드(T1) — 모두 매핑됨.
- 순수 로직(T2,T3,T4)은 실제 JVM 테스트; 스캐폴드(T5~T8)는 컴파일 검증.
- 타입 일관성: `Transcriber`/`DiaryAnalyzer`에 `load()` 추가가 bench 구현·fake·pipeline에서 일치. `AnalysisResult` 필드(Phase 1, pssKb 포함) 재사용.
- 기기 검증(녹음 품질·VAD 효과·end-to-end)은 사용자 몫.
