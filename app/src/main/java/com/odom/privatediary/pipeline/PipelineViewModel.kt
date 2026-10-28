package com.odom.privatediary.pipeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.bench.LiteRtDiaryAnalyzer
import com.odom.privatediary.bench.ModelPaths
import com.odom.privatediary.bench.WhisperTranscriber
import com.odom.privatediary.data.DiaryDatabase
import com.odom.privatediary.data.DiaryRepository
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
    private val repo = DiaryRepository(DiaryDatabase.get(app).entryDao())
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
                val workWav = File(File(app.filesDir, "recordings"), "last.wav")
                val onStage = { s: PipelineStage -> _state.value = _state.value.copy(stage = s) }
                // 회고 생성 등 다른 모델 작업과 겹치지 않게 공용 게이트 안에서 실행.
                // VAD(ONNX)도 게이트 안에서 만들어, 대기 중 취소돼도 해제 안 된 네이티브 자원이 남지 않게 한다.
                val result = withContext(dispatcher) {
                    ModelGate.shared.withModel(onWait = { onStage(PipelineStage.Waiting) }) {
                        DiaryPipeline(
                            vad = SileroVad(app),
                            trimmer = SilenceTrimmer(),
                            transcriber = WhisperTranscriber(app, paths.whisperModel.absolutePath),
                            analyzer = LiteRtDiaryAnalyzer(paths.gemmaModel.absolutePath, app.cacheDir.absolutePath),
                            onStage = onStage,
                            onTranscript = { text -> _state.value = _state.value.copy(transcript = text) },
                        ).run(pcm, workWav)
                    }
                }
                val durationSec = pcm.size / (16000 * 2)
                withContext(dispatcher) {
                    runCatching { repo.saveEntry(_state.value.transcript, result, durationSec) }
                }
                _state.value = _state.value.copy(stage = PipelineStage.Done, analysis = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = PipelineStage.Failed, error = e.message)
            }
        }
    }

    fun stopRecording() = recorder.stop()
}
