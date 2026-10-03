package com.odom.privatediary.bench

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File

class WhisperTranscriber(
    private val context: Context,
    private val modelPath: String,
) : Transcriber {
    private var model: WhisperModel? = null

    suspend fun load() {
        model = Whisper.loadModel(context, modelPath)
    }

    override suspend fun transcribe(wav: File, language: String): TranscriptResult {
        val m = model ?: Whisper.loadModel(context, modelPath).also { model = it }
        val start = System.nanoTime()
        val result = Whisper.transcribe(m, wav.absolutePath, WhisperConfig(language = language))
        val latencyMs = (System.nanoTime() - start) / 1_000_000
        return TranscriptResult(
            text = result.text,
            latencyMs = latencyMs,
            pssKb = Benchmark.currentPssKb(),
        )
    }

    override fun release() {
        model?.let { Whisper.releaseModel(it) }
        model = null
    }
}
