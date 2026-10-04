package com.odom.privatediary.ui

/** 감정 라벨 → ARGB(Long) 고정 매핑. Compose는 Color(argb)로 변환한다.
 *  Color 대신 Long을 노출해 JVM 단위 테스트가 가능하다. */
object EmotionPalette {
    const val NEUTRAL: Long = 0xFFB0BEC5

    private val map = mapOf(
        "기쁨" to 0xFFFFD54F,
        "평온" to 0xFF81C784,
        "설렘" to 0xFFFF8A65,
        "슬픔" to 0xFF64B5F6,
        "불안" to 0xFF9575CD,
        "분노" to 0xFFE57373,
        "피곤" to 0xFF90A4AE,
    )

    fun argb(emotion: String?): Long = map[emotion] ?: NEUTRAL
}
