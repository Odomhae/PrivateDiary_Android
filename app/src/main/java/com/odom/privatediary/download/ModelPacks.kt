package com.odom.privatediary.download

import android.content.Context
import com.google.android.play.core.aipacks.AiPackManagerFactory
import java.io.File

/** Play AI pack 이름. 모듈 `:whisper_pack`, `:gemma_pack`의 `packName`과 같아야 한다. */
object ModelPacks {
    const val WHISPER = "whisper_pack"
    const val GEMMA = "gemma_pack"
    val ALL = listOf(WHISPER, GEMMA)

    /** 팩이 설치 완료됐으면 assets 디렉터리, 아니면 null. */
    fun assetsDir(context: Context, pack: String): File? =
        AiPackManagerFactory.getInstance(context).getPackLocation(pack)?.assetsPath()?.let(::File)
}
