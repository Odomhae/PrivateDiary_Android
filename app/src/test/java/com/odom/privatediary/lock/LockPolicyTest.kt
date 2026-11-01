package com.odom.privatediary.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockPolicyTest {
    private val grace = LockPolicy.RELOCK_AFTER_MS

    @Test fun starts_loading() {
        assertEquals(LockState.Loading, LockPolicy().state)
    }

    @Test fun cold_start_locks_when_enabled() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = true)
        assertEquals(LockState.Locked, p.state)
    }

    @Test fun cold_start_unlocked_when_disabled() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = false, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun not_locked_when_device_cannot_authenticate() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = false)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun later_settings_updates_do_not_relock() {
        val p = unlocked()
        p.onSettingsLoaded(enabled = true, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun short_background_does_not_lock() {
        val p = unlocked()
        p.onBackground(now = 1_000)
        p.onForeground(now = 1_000 + grace - 1, enabled = true, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun long_background_locks() {
        val p = unlocked()
        p.onBackground(now = 1_000)
        p.onForeground(now = 1_000 + grace, enabled = true, available = true)
        assertEquals(LockState.Locked, p.state)
    }

    @Test fun long_background_with_lock_disabled_stays_unlocked() {
        val p = unlocked()
        p.onBackground(now = 0)
        p.onForeground(now = grace * 10, enabled = false, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun foreground_without_background_does_not_lock() {
        val p = unlocked()
        p.onForeground(now = grace * 10, enabled = true, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun background_during_auth_is_ignored() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = true)
        p.onAuthStarted(now = 0)
        p.onBackground(now = 0) // 기기 PIN 화면이 별도 Activity로 뜸
        p.onForeground(now = grace * 10, enabled = true, available = true)
        p.onAuthFinished(success = true)
        p.onForeground(now = grace * 20, enabled = true, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun auth_success_unlocks_and_clears_background() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = true)
        p.onBackground(now = 0)
        p.onAuthStarted(now = 0)
        p.onAuthFinished(success = true)
        p.onForeground(now = grace * 10, enabled = true, available = true)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun auth_failure_stays_locked() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = true)
        p.onAuthStarted(now = 0)
        p.onAuthFinished(success = false)
        assertEquals(LockState.Locked, p.state)
    }

    @Test fun disabling_unlocks_immediately() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = true)
        p.onLockDisabled()
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun locked_unlocks_when_device_lock_removed() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = true, available = true)
        p.onForeground(now = 1, enabled = true, available = false)
        assertEquals(LockState.Unlocked, p.state)
    }

    @Test fun stale_auth_does_not_disable_relock() {
        val p = LockPolicy()
        p.onSettingsLoaded(enabled = false, available = true)
        p.onAuthStarted(now = 0) // 콜백이 끝내 오지 않은 경우
        p.onBackground(now = LockPolicy.AUTH_WINDOW_MS)
        p.onForeground(now = LockPolicy.AUTH_WINDOW_MS + grace, enabled = true, available = true)
        assertEquals(LockState.Locked, p.state)
    }

    private fun unlocked() = LockPolicy().apply { onSettingsLoaded(enabled = false, available = true) }
}
