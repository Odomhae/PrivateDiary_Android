package com.odom.privatediary.download

object DownloadPlan {
    /** 이어받기용 Range 헤더: 이미 받은 바이트부터 끝까지. */
    fun rangeHeader(existingBytes: Long): String = "bytes=$existingBytes-"

    fun isComplete(existingBytes: Long, totalBytes: Long): Boolean =
        totalBytes > 0 && existingBytes >= totalBytes
}
