package com.odom.privatediary.bench

import android.os.Debug

object Benchmark {
    inline fun <T> measureLatency(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start) / 1_000_000
    }

    /** 현재 프로세스 PSS(KB). 추론 직후 호출해 피크 근삿값으로 사용. 기기에서만 유효. */
    fun currentPssKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }
}
