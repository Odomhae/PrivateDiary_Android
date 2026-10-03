package com.odom.privatediary.pipeline

import java.io.ByteArrayOutputStream

/** PCM을 VAD가 요구하는 프레임 크기(vad.frameBytes)로 쪼개 발화 프레임만 남긴다.
 *  프레임보다 짧은 꼬리는 분류 불가라 그대로 보존한다(마지막 음성 잘림 방지).
 *  프레임 크기를 VAD에서 가져오므로 트리머와 VAD가 어긋날 수 없다. */
class SilenceTrimmer {
    fun trim(pcm: ByteArray, vad: VoiceActivityDetector): ByteArray {
        val frameBytes = vad.frameBytes
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
