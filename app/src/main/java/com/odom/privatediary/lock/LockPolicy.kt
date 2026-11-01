package com.odom.privatediary.lock

sealed interface LockState {
    /** 설정을 아직 읽지 못함. 일기 내용이 비치지 않게 빈 화면을 보인다. */
    data object Loading : LockState
    data object Locked : LockState
    data object Unlocked : LockState
}

/**
 * 앱 잠금 판단(순수 로직). 시각은 호출자가 넘긴다.
 * - 콜드 스타트: 잠금이 켜져 있고 기기가 인증 가능하면 잠근다.
 * - 백그라운드에 [RELOCK_AFTER_MS] 이상 있다 돌아오면 잠근다.
 * - 인증 중의 백그라운드 전환(기기 PIN 화면은 별도 Activity)은 기록하지 않는다.
 *   콜백이 끝내 오지 않아도 재잠금이 영영 꺼지지 않게 [AUTH_WINDOW_MS] 동안만 인정한다.
 * - 잠긴 상태에서 기기 화면 잠금이 사라지면 풀 방법이 없으므로 해제한다.
 */
class LockPolicy {
    var state: LockState = LockState.Loading
        private set

    private var backgroundAt: Long? = null
    private var authStartedAt: Long? = null

    fun onSettingsLoaded(enabled: Boolean, available: Boolean) {
        if (state != LockState.Loading) return
        state = if (enabled && available) LockState.Locked else LockState.Unlocked
    }

    private fun authenticating(now: Long): Boolean {
        val started = authStartedAt ?: return false
        if (now - started < AUTH_WINDOW_MS) return true
        authStartedAt = null
        return false
    }

    fun onBackground(now: Long) {
        if (authenticating(now) || state != LockState.Unlocked) return
        backgroundAt = now
    }

    fun onForeground(now: Long, enabled: Boolean, available: Boolean) {
        if (state == LockState.Locked && !available) {
            state = LockState.Unlocked
            backgroundAt = null
            return
        }
        if (authenticating(now)) return
        val since = backgroundAt ?: return
        backgroundAt = null
        if (enabled && available && now - since >= RELOCK_AFTER_MS) state = LockState.Locked
    }

    fun onAuthStarted(now: Long) {
        authStartedAt = now
    }

    fun onAuthFinished(success: Boolean) {
        authStartedAt = null
        backgroundAt = null
        if (success) state = LockState.Unlocked
    }

    fun onLockDisabled() {
        backgroundAt = null
        if (state == LockState.Locked) state = LockState.Unlocked
    }

    companion object {
        const val RELOCK_AFTER_MS = 30_000L
        const val AUTH_WINDOW_MS = 120_000L
    }
}
