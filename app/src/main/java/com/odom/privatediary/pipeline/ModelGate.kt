package com.odom.privatediary.pipeline

import kotlinx.coroutines.sync.Mutex

/**
 * 모델을 쓰는 작업(녹음 파이프라인, 주간 회고)을 한 번에 하나만 실행한다.
 * Whisper·Gemma를 동시에 메모리에 올리지 않기 위한 앱 전역 잠금.
 */
class ModelGate {
    private val mutex = Mutex()

    /** 다른 작업이 실행 중이면 [onWait]을 부른 뒤 끝날 때까지 기다린다. */
    suspend fun <T> withModel(onWait: () -> Unit = {}, block: suspend () -> T): T {
        if (!mutex.tryLock()) {
            onWait()
            mutex.lock()
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        val shared = ModelGate()
    }
}
