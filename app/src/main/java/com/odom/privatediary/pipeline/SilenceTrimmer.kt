package com.odom.privatediary.pipeline

import java.io.ByteArrayOutputStream

/** PCM을 frameBytes 단위로 쪼개 VAD가 발화로 판정한 프레임만 남긴다.
 *  프레임보다 짧은 꼬리는 분류 불가라 그대로 보존한다(마지막 음성 잘림 방지). */
class SilenceTrimmer(private val frameBytes: Int = 1024) {
    fun trim(pcm: ByteArray, vad: VoiceActivityDetector): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i + frameBytes <= pcm.size) {
            val frame = pcm.copyOfRange(i, i + frameBytes)
            if (vad.isSpeech(frame)) out.write(frame)
            i += frameBytes
        }
        if (i < pcm.size) out.write(pcm, i, pcm.size - i)
        return out.toByteArray()
    }
}
