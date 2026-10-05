package com.odom.privatediary.download

object R2Config {
    /** 릴리스 전 실제 Cloudflare R2 공개 base URL로 교체할 것(플레이스홀더). */
    const val BASE_URL = "https://REPLACE-ME.r2.example/models"
    const val MANIFEST_PATH = "manifest.json"

    fun url(path: String): String = BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    fun manifestUrl(): String = url(MANIFEST_PATH)
}
