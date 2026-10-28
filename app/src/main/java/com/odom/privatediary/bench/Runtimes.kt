package com.odom.privatediary.bench

import java.io.File

interface Transcriber {
    suspend fun load()
    suspend fun transcribe(wav: File, language: String = "ko"): TranscriptResult
    fun release()
}

/** 자유 텍스트 생성(주간 회고 등). 런타임 교체 가능하도록 인터페이스 뒤에 둔다. */
interface TextGenerator {
    suspend fun load()
    suspend fun generate(system: String, prompt: String): String
    fun release()
}

interface DiaryAnalyzer {
    suspend fun load()
    suspend fun analyze(transcript: String): AnalysisResult
    fun release()
}
