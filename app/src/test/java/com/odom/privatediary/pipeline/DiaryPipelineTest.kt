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
        val wav = File.createTempFile("pipe", ".wav")
        val pipeline = DiaryPipeline(passVad, SilenceTrimmer(4), fakeTranscriber("안녕", calls), fakeAnalyzer(calls)) { stages += it }
        val result = pipeline.run(ByteArray(8) { 1 }, wav)
        assertEquals("평온", result.emotion)
        assertEquals(
            listOf(PipelineStage.Trimming, PipelineStage.Transcribing, PipelineStage.Analyzing, PipelineStage.Done),
            stages,
        )
        assertEquals(
            listOf("t.load", "t.transcribe", "t.release", "a.load", "a.analyze:안녕", "a.release"),
            calls,
        )
    }

    @Test fun transcript_is_passed_to_analyzer() = runTest {
        val calls = mutableListOf<String>()
        val wav = File.createTempFile("pipe", ".wav")
        DiaryPipeline(passVad, SilenceTrimmer(4), fakeTranscriber("오늘 피곤", calls), fakeAnalyzer(calls)) {}
            .run(ByteArray(4) { 1 }, wav)
        assertTrue(calls.contains("a.analyze:오늘 피곤"))
    }

    @Test fun analyzer_failure_propagates_without_done() = runTest {
        val stages = mutableListOf<PipelineStage>()
        val calls = mutableListOf<String>()
        val wav = File.createTempFile("pipe", ".wav")
        val pipeline = DiaryPipeline(passVad, SilenceTrimmer(4), fakeTranscriber("x", calls), fakeAnalyzer(calls, throwIt = true)) { stages += it }
        var threw = false
        try { pipeline.run(ByteArray(4) { 1 }, wav) } catch (e: Exception) { threw = true }
        assertTrue(threw)
        assertTrue(!stages.contains(PipelineStage.Done))
    }
}
