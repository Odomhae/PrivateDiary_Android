package com.odom.privatediary.bench

import android.content.Context
import com.odom.privatediary.download.ModelPacks
import java.io.File

class ModelPaths(whisperDir: File, gemmaDir: File) {
    constructor(modelsDir: File) : this(modelsDir, modelsDir)

    val whisperModel: File = File(whisperDir, WHISPER_FILE)
    val gemmaModel: File = File(gemmaDir, GEMMA_FILE)

    fun whisperReady(): Boolean = whisperModel.exists() && whisperModel.length() > 0
    fun gemmaReady(): Boolean = gemmaModel.exists() && gemmaModel.length() > 0

    companion object {
        const val WHISPER_FILE = "ggml-small.bin"
        const val GEMMA_FILE = "gemma3-1b-it-int4.litertlm"

        /**
         * Play AI pack 위치를 우선 쓰고, 팩이 없으면(Android Studio 직접 설치 등) adb로 넣은
         * `files/models`로 대체한다. 팩 위치는 앱 업데이트·데이터 삭제로 바뀔 수 있어 매번 조회한다.
         */
        fun from(context: Context): ModelPaths {
            val sideload = File(context.filesDir, "models").apply { mkdirs() }
            return ModelPaths(
                whisperDir = resolveDir(ModelPacks.assetsDir(context, ModelPacks.WHISPER), sideload, WHISPER_FILE),
                gemmaDir = resolveDir(ModelPacks.assetsDir(context, ModelPacks.GEMMA), sideload, GEMMA_FILE),
            )
        }

        /** 팩 디렉터리에 파일이 있으면 그 디렉터리, 아니면 사이드로드 디렉터리. */
        fun resolveDir(packDir: File?, sideloadDir: File, fileName: String): File =
            packDir?.takeIf { File(it, fileName).exists() } ?: sideloadDir
    }
}
