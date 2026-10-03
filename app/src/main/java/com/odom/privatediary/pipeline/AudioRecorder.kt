package com.odom.privatediary.pipeline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 16kHz mono 16-bit PCM 녹음. stop() 또는 maxMs 도달 시 종료. RECORD_AUDIO 권한 필요. */
class AudioRecorder(private val sampleRate: Int = 16000) {
    @Volatile private var recording = false

    @SuppressLint("MissingPermission")
    suspend fun record(maxMs: Long = 60_000): ByteArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf,
        )
        val out = ByteArrayOutputStream()
        val buf = ByteArray(minBuf)
        recorder.startRecording()
        recording = true
        val deadline = System.currentTimeMillis() + maxMs
        while (recording && System.currentTimeMillis() < deadline) {
            val n = recorder.read(buf, 0, buf.size)
            if (n > 0) out.write(buf, 0, n)
        }
        recorder.stop()
        recorder.release()
        out.toByteArray()
    }

    fun stop() {
        recording = false
    }
}
