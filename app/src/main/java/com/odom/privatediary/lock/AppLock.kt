package com.odom.privatediary.lock

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import com.odom.privatediary.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 앱 범위 잠금 상태. [LockPolicy]에 설정·기기 가용성·시각을 넣어 준다. */
class AppLock private constructor(context: Context) {
    private val app = context.applicationContext
    private val settings = SettingsStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = LockPolicy()

    private val _state = MutableStateFlow(policy.state)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    /** 잠금 화면 인증 오류 문구. 회전 후에도 남도록 컴포지션 밖에 둔다. */
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        scope.launch {
            settings.lockEnabled.collect { on ->
                _enabled.value = on
                if (!on) policy.onLockDisabled()
                policy.onSettingsLoaded(on, isAvailable())
                publish()
            }
        }
    }

    /** 지문·얼굴 또는 기기 PIN/패턴/비밀번호로 인증할 수 있는가. */
    fun isAvailable(): Boolean =
        BiometricManager.from(app).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onBackground() {
        policy.onBackground(SystemClock.elapsedRealtime())
    }

    fun onForeground() {
        policy.onForeground(SystemClock.elapsedRealtime(), _enabled.value, isAvailable())
        publish()
    }

    /**
     * 인증을 띄우고 결과를 정책에 반영한다. 프롬프트를 띄울 수 없는 상태(저장된 Fragment 상태)면 아무것도 하지 않는다.
     * [onResult]는 화면별 후처리(설정에서 잠금 켜기 등).
     */
    fun authenticate(activity: FragmentActivity, title: String, onResult: (AuthResult) -> Unit = {}) {
        if (activity.supportFragmentManager.isStateSaved) return
        _authError.value = null
        policy.onAuthStarted(SystemClock.elapsedRealtime())
        BiometricAuth.authenticate(activity, title) { r ->
            policy.onAuthFinished(r == AuthResult.Success)
            _authError.value = (r as? AuthResult.Error)?.message
            publish()
            onResult(r)
        }
    }

    suspend fun setEnabled(on: Boolean) {
        settings.setLockEnabled(on)
    }

    private fun publish() {
        _state.value = policy.state
    }

    companion object {
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        // applicationContext만 보관하므로 Activity 누수가 아니다.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: AppLock? = null

        fun get(context: Context): AppLock = instance ?: synchronized(this) {
            instance ?: AppLock(context).also { instance = it }
        }
    }
}
