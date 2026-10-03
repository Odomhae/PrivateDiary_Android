package com.odom.privatediary.pipeline

interface VoiceActivityDetector {
    /** VAD가 한 번에 분류하는 프레임 크기(byte). 트리머는 이 값으로 PCM을 쪼갠다. */
    val frameBytes: Int
    fun isSpeech(frame: ByteArray): Boolean
    fun release()
}
