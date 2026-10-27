package com.odom.privatediary.data

object SearchQuery {
    /** 검색어를 `ESCAPE '\'` LIKE 부분 일치 패턴으로. 빈 검색어는 null(검색하지 않음). */
    fun toLikePattern(query: String): String? {
        val trimmed = query.trim().ifEmpty { return null }
        val escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }
}
