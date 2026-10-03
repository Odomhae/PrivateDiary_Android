package com.odom.privatediary.pipeline

interface VoiceActivityDetector {
    fun isSpeech(frame: ByteArray): Boolean
    fun release()
}
