package com.odom.privatediary.bench

import android.content.Context
import java.io.File

class ModelPaths(modelsDir: File) {
    val whisperModel: File = File(modelsDir, "ggml-small.bin")
    val gemmaModel: File = File(modelsDir, "gemma-3n-E2B-it.litertlm")

    fun whisperReady(): Boolean = whisperModel.exists() && whisperModel.length() > 0
    fun gemmaReady(): Boolean = gemmaModel.exists() && gemmaModel.length() > 0

    companion object {
        fun from(context: Context): ModelPaths =
            ModelPaths(File(context.filesDir, "models").apply { mkdirs() })
    }
}
