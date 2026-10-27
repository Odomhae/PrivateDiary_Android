package com.odom.privatediary.ui

object SearchSnippet {
    /** 첫 일치 위치 앞뒤 [radius]자를 잘라 보여준다. 일치가 없으면 앞부분. */
    fun around(text: String, query: String, radius: Int = 20): String {
        val q = query.trim()
        val idx = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
        if (idx < 0) return if (text.length > radius * 2) text.take(radius * 2) + "…" else text
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + q.length + radius).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
    }
}
