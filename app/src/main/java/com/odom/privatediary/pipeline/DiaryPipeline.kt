package com.odom.privatediary.pipeline

import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.bench.DiaryAnalyzer
import com.odom.privatediary.bench.Transcriber
import com.odom.privatediary.safety.CrisisDetector
import java.io.File

/** 녹음 PCM을 받아 VAD 트리밍 → 전사 → 분석 순서로 처리한다.
 *  각 모델을 쓰는 순간에만 load/release 하여 Whisper·Gemma 동시 적재를 막는다.
 *  각 단계는 try/finally로 감싸 예외가 나도 해당 모델을 반드시 release 한다(누수 방지). */
class DiaryPipeline(
    private val vad: VoiceActivityDetector,
    private val trimmer: SilenceTrimmer,
    private val transcriber: Transcriber,
    private val analyzer: DiaryAnalyzer,
    private val onStage: (PipelineStage) -> Unit = {},
    private val onTranscript: (String) -> Unit = {},
) {
    suspend fun run(pcm: ByteArray, workWav: File): AnalysisResult {
        onStage(PipelineStage.Trimming)
        val trimmed = try {
            trimmer.trim(pcm, vad)
        } finally {
            vad.release()
        }
        WavWriter.writeWav(workWav, trimmed)

        onStage(PipelineStage.Transcribing)
        val transcript = try {
            transcriber.load()
            transcriber.transcribe(workWav)
        } finally {
            transcriber.release()
        }
        onTranscript(transcript.text)
        // 규칙 기반 위기 감지가 1차, LLM crisis는 보조 (스펙 §5.3).
        val ruleCrisis = CrisisDetector.detect(transcript.text)

        onStage(PipelineStage.Analyzing)
        val result = try {
            analyzer.load()
            analyzer.analyze(transcript.text)
        } finally {
            analyzer.release()
        }

        onStage(PipelineStage.Done)
        return result.copy(crisis = ruleCrisis || result.crisis)
    }
}
