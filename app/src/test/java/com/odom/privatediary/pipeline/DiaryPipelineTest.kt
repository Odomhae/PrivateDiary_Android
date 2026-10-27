package com.odom.privatediary.pipeline

import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.bench.DiaryAnalyzer
import com.odom.privatediary.bench.Transcriber
import com.odom.privatediary.bench.TranscriptResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiaryPipelineTest {
    private val passVad = object : VoiceActivityDetector {
        override val frameBytes = 4
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
        val pipeline = DiaryPipeline(passVad, SilenceTrimmer(), fakeTranscriber("안녕", calls), fakeAnalyzer(calls), onStage = { stages += it })
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
        DiaryPipeline(passVad, SilenceTrimmer(), fakeTranscriber("오늘 피곤", calls), fakeAnalyzer(calls)) {}
            .run(ByteArray(4) { 1 }, wav)
        assertTrue(calls.contains("a.analyze:오늘 피곤"))
    }

    @Test fun analyzer_failure_returns_failed_result_and_releases() = runTest {
        val calls = mutableListOf<String>()
        val wav = File.createTempFile("pipe", ".wav")
        val pipeline = DiaryPipeline(passVad, SilenceTrimmer(), fakeTranscriber("x", calls), fakeAnalyzer(calls, throwIt = true))
        val result = pipeline.run(ByteArray(4) { 1 }, wav)
        assertFalse(result.parseOk)
        assertFalse(result.crisis)
        // 실패해도 각 모델은 해제되어야 한다(누수 방지, I1)
        assertTrue(calls.contains("t.release"))
        assertTrue(calls.contains("a.release"))
    }

    @Test fun rule_crisis_survives_analyzer_failure() = runTest {
        val wav = File.createTempFile("pipe", ".wav")
        val result = DiaryPipeline(passVad, SilenceTrimmer(), fakeTranscriber("죽고 싶어", mutableListOf()), fakeAnalyzer(mutableListOf(), throwIt = true))
            .run(ByteArray(4) { 1 }, wav)
        assertFalse(result.parseOk)
        assertTrue(result.crisis)
    }

    @Test fun rule_crisis_overrides_llm_false() = runTest {
        val wav = File.createTempFile("pipe", ".wav")
        val result = DiaryPipeline(passVad, SilenceTrimmer(), fakeTranscriber("요즘 죽고 싶어", mutableListOf()), fakeAnalyzer(mutableListOf())) {}
            .run(ByteArray(4) { 1 }, wav)
        assertTrue(result.crisis)
    }

    @Test fun no_crisis_when_rule_and_llm_both_false() = runTest {
        val wav = File.createTempFile("pipe", ".wav")
        val result = DiaryPipeline(passVad, SilenceTrimmer(), fakeTranscriber("오늘 산책했어", mutableListOf()), fakeAnalyzer(mutableListOf())) {}
            .run(ByteArray(4) { 1 }, wav)
        assertFalse(result.crisis)
    }
}
