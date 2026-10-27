package com.odom.privatediary.bench

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object DiaryJson {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** LLM 출력에서 첫 번째 균형 잡힌 {...} 객체 문자열을 추출. 없으면 null. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until raw.length) {
            when (raw[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return raw.substring(start, i + 1) }
            }
        }
        return null
    }

    /** 추출 → 파싱 → 고정 라벨 정규화. 실패 시 parseOk=false(raw 보존). */
    fun parse(raw: String): AnalysisResult {
        val objStr = extractJsonObject(raw) ?: return failed(raw)
        return try {
            val obj = json.parseToJsonElement(objStr) as JsonObject
            fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: ""
            fun arr(key: String) =
                obj[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            AnalysisResult(
                summary = str("summary"),
                emotion = DiarySchema.normalizeEmotion(str("emotion")),
                intensity = DiarySchema.clampIntensity(obj["intensity"]?.jsonPrimitive?.intOrNull ?: 1),
                causes = DiarySchema.normalizeCauses(arr("causes")),
                keywords = arr("keywords").map { it.trim() }.filter { it.isNotEmpty() }.take(3),
                reply = str("reply"),
                crisis = obj["crisis"]?.jsonPrimitive?.booleanOrNull ?: false,
                raw = raw,
                parseOk = true,
            )
        } catch (e: Exception) {
            failed(raw)
        }
    }

    fun failed(raw: String) = AnalysisResult(
        summary = "", emotion = null, intensity = 1, causes = emptyList(),
        keywords = emptyList(), reply = "", crisis = false, raw = raw, parseOk = false,
    )
}
