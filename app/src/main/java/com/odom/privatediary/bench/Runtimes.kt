package com.odom.privatediary.bench

import java.io.File

interface Transcriber {
    suspend fun load()
    suspend fun transcribe(wav: File, language: String = "ko"): TranscriptResult
    fun release()
}

interface DiaryAnalyzer {
    suspend fun load()
    suspend fun analyze(transcript: String): AnalysisResult
    fun release()
}
