package com.odom.privatediary.bench

data class TranscriptResult(
    val text: String,
    val latencyMs: Long,
    val peakRssKb: Long,
)

data class AnalysisResult(
    val summary: String,
    val emotion: String?,
    val intensity: Int,
    val causes: List<String>,
    val keywords: List<String>,
    val reply: String,
    val crisis: Boolean,
    val raw: String,
    val parseOk: Boolean,
    val latencyMs: Long = 0,
    val peakRssKb: Long = 0,
)
