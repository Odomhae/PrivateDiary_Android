package com.odom.privatediary.bench

object DiarySchema {
    val EMOTIONS = listOf("기쁨", "평온", "설렘", "슬픔", "불안", "분노", "피곤")
    val CAUSES = listOf("일", "관계", "건강", "수면", "돈", "자기계발", "기타")

    fun normalizeEmotion(value: String?): String? =
        value?.trim()?.takeIf { it in EMOTIONS }

    fun normalizeCauses(values: List<String>): List<String> =
        values.map { it.trim() }.filter { it in CAUSES }.distinct().take(3)

    fun clampIntensity(value: Int): Int = value.coerceIn(1, 5)
}
