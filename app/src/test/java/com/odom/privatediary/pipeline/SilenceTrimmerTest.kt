package com.odom.privatediary.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceTrimmerTest {
    private fun vadFirstByteOne() = object : VoiceActivityDetector {
        override fun isSpeech(frame: ByteArray) = frame.isNotEmpty() && frame[0].toInt() == 1
        override fun release() {}
    }

    @Test fun keeps_only_speech_frames() {
        val speech = ByteArray(4) { 1 }
        val silence = ByteArray(4) { 0 }
        val pcm = silence + speech + silence + speech
        val out = SilenceTrimmer(frameBytes = 4).trim(pcm, vadFirstByteOne())
        assertArrayEquals(speech + speech, out)
    }

    @Test fun all_silence_yields_empty() {
        val pcm = ByteArray(16) { 0 }
        assertEquals(0, SilenceTrimmer(frameBytes = 4).trim(pcm, vadFirstByteOne()).size)
    }

    @Test fun trailing_partial_frame_is_kept() {
        val speech = ByteArray(4) { 1 }
        val tail = byteArrayOf(1, 2)
        val out = SilenceTrimmer(frameBytes = 4).trim(speech + tail, vadFirstByteOne())
        assertArrayEquals(speech + tail, out)
    }
}
