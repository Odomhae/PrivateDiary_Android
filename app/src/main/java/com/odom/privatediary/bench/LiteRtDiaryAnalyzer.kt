package com.odom.privatediary.bench

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

class LiteRtDiaryAnalyzer(
    private val modelPath: String,
    private val cacheDir: String,
) : DiaryAnalyzer {
    private var engine: Engine? = null

    /** 초기화는 최대 10초가 걸릴 수 있으므로 백그라운드 디스패처에서 호출한다. */
    suspend fun load() {
        val e = Engine(
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
        )
        e.initialize()
        engine = e
    }

    override suspend fun analyze(transcript: String): AnalysisResult {
        val e = engine ?: error("Engine not loaded; call load() first")
        val start = System.nanoTime()
        val result = AnalysisRunner.analyzeWithRetry(transcript) { prompt ->
            e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(DiaryPrompt.SYSTEM_INSTRUCTION),
                    samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.4),
                )
            ).use { conversation -> conversation.sendMessage(prompt).toString() }
        }
        val latencyMs = (System.nanoTime() - start) / 1_000_000
        return result.copy(latencyMs = latencyMs, peakRssKb = Benchmark.currentPssKb())
    }

    override fun release() {
        engine?.close()
        engine = null
    }
}
