package com.odom.privatediary.safety

/**
 * 자해·극단적 선택 암시 표현의 규칙 기반 1차 감지 (스펙 §5.3). LLM `crisis`는 보조.
 * 안전 쪽 오탐(부정문 등)은 감수한다.
 */
object CrisisDetector {
    /** 공백·문장부호를 지운 전체 텍스트에서 찾는 구절 (Whisper 띄어쓰기 흔들림 대응). */
    val PHRASES = listOf(
        "죽고싶", "죽어버리고싶", "극단적선택", "목숨을끊", "목을매", "손목을긋",
        "뛰어내리고싶", "살기싫", "사라지고싶", "없어지고싶", "생을마감",
    )

    /** 어절 안에서만 찾는 짧은 단어. 공백을 지우면 "여자 살"→"여자살"처럼 경계를 넘어 오탐한다. */
    val WORDS = listOf("자살", "자해", "유서")

    fun normalize(text: String): String = text.filter { it.isLetterOrDigit() }

    fun detect(text: String): Boolean {
        val normalized = normalize(text)
        if (PHRASES.any { normalized.contains(it) }) return true
        return text.split(WHITESPACE).map(::normalize).any { word -> WORDS.any { word.contains(it) } }
    }

    private val WHITESPACE = Regex("\\s+")
}
