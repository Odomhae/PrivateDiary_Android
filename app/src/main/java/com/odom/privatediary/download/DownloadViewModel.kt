package com.odom.privatediary.download

import android.app.Application
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackState
import com.google.android.play.core.aipacks.AiPackStateUpdateListener
import com.google.android.play.core.aipacks.AiPackStates
import com.google.android.play.core.aipacks.model.AiPackErrorCode
import com.google.android.play.core.aipacks.model.AiPackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** fast-follow AI pack 상태를 추적한다. 다운로드 자체는 Play 스토어가 수행한다. */
class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val manager = AiPackManagerFactory.getInstance(app)
    private val snapshots = MutableStateFlow<Map<String, PackSnapshot>>(emptyMap())

    val state: StateFlow<PackUiState> = snapshots
        .map { PackProgress.reduce(it, ModelPacks.ALL) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PackUiState.Checking)

    private val listener = AiPackStateUpdateListener { s -> put(s.toSnapshot()) }

    init {
        manager.registerListener(listener)
        refresh()
    }

    /** 현재 상태를 조회하고, 아직 요청되지 않은 팩이 있으면 바로 fetch한다. */
    fun refresh() {
        manager.getPackStates(ModelPacks.ALL).track { states ->
            if (states.packStates().values.any { it.status() in FETCHABLE }) fetch()
        }
    }

    /** 다운로드 요청(실패 후 재시도 포함). */
    fun fetch() {
        manager.fetch(ModelPacks.ALL).track()
    }

    /** Wi-Fi 대기·사용자 확인 상태에서 Play의 확인 대화상자를 띄운다. */
    fun confirm(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.showConfirmationDialog(launcher)
    }

    private fun Task<AiPackStates>.track(onStates: (AiPackStates) -> Unit = {}) {
        addOnSuccessListener { states ->
            states.packStates().values.forEach { put(it.toSnapshot()) }
            onStates(states)
        }
        addOnFailureListener { e ->
            val code = (e as? ApiException)?.statusCode ?: AiPackErrorCode.INTERNAL_ERROR
            snapshots.value = ModelPacks.ALL.associateWith { PackSnapshot(it, AiPackStatus.FAILED, code) }
        }
    }

    private fun put(s: PackSnapshot) = snapshots.update { it + (s.name to s) }

    override fun onCleared() {
        manager.unregisterListener(listener)
    }

    private companion object {
        val FETCHABLE = setOf(AiPackStatus.NOT_INSTALLED, AiPackStatus.CANCELED, AiPackStatus.UNKNOWN)

        fun AiPackState.toSnapshot() =
            PackSnapshot(name(), status(), errorCode(), bytesDownloaded(), totalBytesToDownload())
    }
}
