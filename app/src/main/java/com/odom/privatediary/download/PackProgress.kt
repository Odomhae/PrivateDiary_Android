package com.odom.privatediary.download

import com.google.android.play.core.aipacks.model.AiPackErrorCode
import com.google.android.play.core.aipacks.model.AiPackStatus

/** AiPackState에서 화면에 필요한 값만 뽑은 것 (JVM 테스트용). */
data class PackSnapshot(
    val name: String,
    val status: Int,
    val errorCode: Int = AiPackErrorCode.NO_ERROR,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
)

sealed interface PackUiState {
    data object Checking : PackUiState
    /** 아직 요청 전이거나 취소됨 → fetch 필요. */
    data object NotStarted : PackUiState
    data class Downloading(val percent: Int) : PackUiState
    /** Play가 Wi-Fi를 기다리는 중. 모바일 데이터 사용은 사용자 확인으로만. */
    data object WaitingForWifi : PackUiState
    data object NeedsConfirmation : PackUiState
    data class Failed(val message: String) : PackUiState
    data object Ready : PackUiState
}

object PackProgress {
    fun reduce(snapshots: Map<String, PackSnapshot>, expected: List<String>): PackUiState {
        val packs = expected.map { snapshots[it] ?: return PackUiState.Checking }
        if (packs.all { it.status == AiPackStatus.COMPLETED }) return PackUiState.Ready
        packs.firstOrNull { it.status == AiPackStatus.FAILED || it.errorCode != AiPackErrorCode.NO_ERROR }
            ?.let { return PackUiState.Failed(errorMessage(it.errorCode)) }
        if (packs.any { it.status == AiPackStatus.REQUIRES_USER_CONFIRMATION }) return PackUiState.NeedsConfirmation
        if (packs.any { it.status == AiPackStatus.WAITING_FOR_WIFI }) return PackUiState.WaitingForWifi
        if (packs.any { it.status in NEEDS_FETCH }) return PackUiState.NotStarted
        val total = packs.sumOf { it.totalBytes }
        val done = packs.sumOf { it.bytesDownloaded }
        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 99) else 0
        return PackUiState.Downloading(pct)
    }

    private val NEEDS_FETCH = setOf(AiPackStatus.NOT_INSTALLED, AiPackStatus.CANCELED, AiPackStatus.UNKNOWN)

    fun errorMessage(code: Int): String = when (code) {
        AiPackErrorCode.INSUFFICIENT_STORAGE -> "저장 공간이 부족합니다. 1.1GB 이상 공간을 비운 뒤 다시 시도해 주세요."
        AiPackErrorCode.NETWORK_ERROR -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
        AiPackErrorCode.APP_UNAVAILABLE, AiPackErrorCode.PACK_UNAVAILABLE,
        AiPackErrorCode.APP_NOT_OWNED, AiPackErrorCode.UNRECOGNIZED_INSTALLATION ->
            "Play 스토어에서 설치한 앱에서만 모델을 받을 수 있습니다."
        AiPackErrorCode.API_NOT_AVAILABLE -> "이 기기의 Play 스토어가 모델 다운로드를 지원하지 않습니다. Play 스토어를 업데이트해 주세요."
        else -> "모델을 받지 못했습니다 (오류 $code). 다시 시도해 주세요."
    }
}
