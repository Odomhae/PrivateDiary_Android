package com.odom.privatediary.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** OkHttp 기반 Range 이어받기 다운로더. `*.part`에 받은 뒤 완료 시 rename. */
class ModelDownloader(private val client: OkHttpClient = OkHttpClient()) {

    fun fetchText(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.string() ?: error("empty body")
        }

    suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val existing = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(url)
            .header("Range", DownloadPlan.rangeHeader(existing))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            val append = existing > 0 && resp.code == 206
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(1 shl 16)
                    var downloaded = if (append) existing else 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, expectedBytes)
                    }
                }
            }
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }
}
