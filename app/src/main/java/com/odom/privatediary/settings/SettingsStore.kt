package com.odom.privatediary.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")

// files/datastore/settings.preferences_pb — 클라우드 백업 제외 대상(file 도메인).
// 파일이 깨지면 잠금 켜짐으로 되살린다(앱이 죽지 않고, 잠금이 몰래 풀리지도 않게). 인증 불가 기기면 AppLock이 잠그지 않는다.
private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { preferencesOf(LOCK_ENABLED to true) },
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    /** 읽기 실패(IOException) 시 잠금 켜짐으로 본다(fail-closed). */
    val lockEnabled: Flow<Boolean> = store.data
        .map { it[LOCK_ENABLED] ?: false }
        .catch { if (it is IOException) emit(true) else throw it }

    suspend fun setLockEnabled(enabled: Boolean) {
        store.edit { it[LOCK_ENABLED] = enabled }
    }
}
