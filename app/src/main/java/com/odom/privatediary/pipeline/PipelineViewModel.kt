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
        val stage = _state.value.stage
        if (stage != PipelineStage.Idle && stage != PipelineStage.Done && stage != PipelineStage.Failed) return
        _state.value = PipelineUiState(stage = PipelineStage.Recording)
        viewModelScope.launch {
            try {
                val pcm = recorder.record()
                val app = getApplication<Application>()
                val pipeline = DiaryPipeline(
                    vad = SileroVad(app),
                    trimmer = SilenceTrimmer(),
                    transcriber = WhisperTranscriber(app, paths.whisperModel.absolutePath),
                    analyzer = LiteRtDiaryAnalyzer(paths.gemmaModel.absolutePath, app.cacheDir.absolutePath),
                    onStage = { s -> _state.value = _state.value.copy(stage = s) },
                    onTranscript = { text -> _state.value = _state.value.copy(transcript = text) },
                )
                val workWav = File(File(app.filesDir, "recordings"), "last.wav")
                val result = withContext(dispatcher) { pipeline.run(pcm, workWav) }
                _state.value = _state.value.copy(stage = PipelineStage.Done, analysis = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = PipelineStage.Failed, error = e.message)
            }
        }
    }

    fun stopRecording() = recorder.stop()
}
