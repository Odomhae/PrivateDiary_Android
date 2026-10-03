package com.odom.privatediary.bench

object AnalysisRunner {
    /** generate(prompt) → JSON 파싱. 실패 시 1회 재시도. */
    suspend fun analyzeWithRetry(
        transcript: String,
        generate: suspend (String) -> String,
    ): AnalysisResult {
        val prompt = DiaryPrompt.buildUserPrompt(transcript)
        val first = DiaryJson.parse(generate(prompt))
        if (first.parseOk) return first
        return DiaryJson.parse(generate(prompt))
    }
}
