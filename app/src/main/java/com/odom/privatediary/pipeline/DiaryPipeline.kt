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
