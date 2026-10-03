package com.odom.privatediary.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class WavWriterTest {
    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    @Test fun header_and_sizes_are_correct() {
        val pcm = ByteArray(100) { it.toByte() }
        val w = WavWriter.wavBytes(pcm)
        assertEquals(144, w.size)
        assertEquals("RIFF", String(w, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 100, le32(w, 4))
        assertEquals("WAVE", String(w, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(w, 12, 4, Charsets.US_ASCII))
        assertEquals(16, le32(w, 16))
        assertEquals(1, le16(w, 20))
        assertEquals(1, le16(w, 22))
        assertEquals(16000, le32(w, 24))
        assertEquals(16000 * 1 * 16 / 8, le32(w, 28))
        assertEquals(2, le16(w, 32))
        assertEquals(16, le16(w, 34))
        assertEquals("data", String(w, 36, 4, Charsets.US_ASCII))
        assertEquals(100, le32(w, 40))
    }

    @Test fun pcm_payload_is_appended_verbatim() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val w = WavWriter.wavBytes(pcm)
        assertEquals(1, w[44].toInt())
        assertEquals(4, w[47].toInt())
    }
}
