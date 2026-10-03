package com.odom.privatediary.pipeline

import android.content.Context
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate

/** android-vad의 Silero VAD 래퍼. 프레임은 512 sample(=1024 byte, 16-bit)이어야 한다. */
class SileroVad(context: Context) : VoiceActivityDetector {
    private val vad = VadSilero(
        context,
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_512,
        mode = Mode.NORMAL,
        silenceDurationMs = 300,
        speechDurationMs = 50,
    )

    /** FRAME_SIZE_512 sample × 16-bit = 1024 byte. isSpeech는 정확히 이 크기를 요구한다. */
    override val frameBytes: Int = 512 * 2

    override fun isSpeech(frame: ByteArray): Boolean = vad.isSpeech(frame)

    override fun release() = vad.close()
}
