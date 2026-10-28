package com.odom.privatediary.report

object ReportBody {
    const val MAX_CHARS = 700
    private val QUOTES = charArrayOf('"', '\'', '“', '”', '‘', '’')

    /** LLM 출력에서 코드펜스·감싸는 따옴표를 걷어내고 길이를 제한한다. 비면 null. */
    fun clean(raw: String): String? {
        val text = raw.replace("```", "").trim().trim(*QUOTES).trim()
        return text.take(MAX_CHARS).ifBlank { null }
    }
}
