package com.odom.privatediary.bench

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

class LiteRtTextGenerator(
    private val modelPath: String,
    private val cacheDir: String,
) : TextGenerator {
    private var engine: Engine? = null

    /** 초기화는 최대 10초가 걸릴 수 있으므로 백그라운드 디스패처에서 호출한다. */
    override suspend fun load() {
        val e = Engine(
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
        )
        e.initialize()
        engine = e
    }

    override suspend fun generate(system: String, prompt: String): String {
        val e = engine ?: error("Engine not loaded; call load() first")
        return e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6),
            )
        ).use { conversation -> conversation.sendMessage(prompt).toString() }
    }

    override fun release() {
        engine?.close()
        engine = null
    }
}
