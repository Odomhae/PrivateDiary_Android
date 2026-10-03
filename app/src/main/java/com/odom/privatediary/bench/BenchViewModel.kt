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
