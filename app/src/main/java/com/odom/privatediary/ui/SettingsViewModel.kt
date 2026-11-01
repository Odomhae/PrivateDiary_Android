package com.odom.privatediary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odom.privatediary.lock.AppLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val lock = AppLock.get(app)
    val lockEnabled: StateFlow<Boolean> = lock.enabled

    private val _lockAvailable = MutableStateFlow(lock.isAvailable())
    /** 기기 화면 잠금이 없으면 false. 설정 앱에서 돌아올 때 [refreshAvailability]로 갱신. */
    val lockAvailable: StateFlow<Boolean> = _lockAvailable.asStateFlow()

    fun refreshAvailability() {
        _lockAvailable.value = lock.isAvailable()
    }

    fun setLockEnabled(on: Boolean) {
        viewModelScope.launch { lock.setEnabled(on) }
    }
}
