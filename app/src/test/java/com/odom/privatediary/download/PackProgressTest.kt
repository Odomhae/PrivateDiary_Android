package com.odom.privatediary.download

import com.google.android.play.core.aipacks.model.AiPackErrorCode
import com.google.android.play.core.aipacks.model.AiPackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackProgressTest {
    private val names = listOf("a", "b")
    private fun of(vararg s: PackSnapshot) = s.associateBy { it.name }

    @Test fun checking_until_every_pack_reported() {
        assertEquals(PackUiState.Checking, PackProgress.reduce(emptyMap(), names))
        assertEquals(PackUiState.Checking, PackProgress.reduce(of(PackSnapshot("a", AiPackStatus.COMPLETED)), names))
    }

    @Test fun ready_when_all_completed() {
        val s = of(PackSnapshot("a", AiPackStatus.COMPLETED), PackSnapshot("b", AiPackStatus.COMPLETED))
        assertEquals(PackUiState.Ready, PackProgress.reduce(s, names))
    }

    @Test fun downloading_percent_sums_bytes_and_caps_below_100() {
        val s = of(
            PackSnapshot("a", AiPackStatus.COMPLETED, bytesDownloaded = 300, totalBytes = 300),
            PackSnapshot("b", AiPackStatus.DOWNLOADING, bytesDownloaded = 200, totalBytes = 700),
        )
        assertEquals(PackUiState.Downloading(50), PackProgress.reduce(s, names))
        val transferring = of(
            PackSnapshot("a", AiPackStatus.COMPLETED, bytesDownloaded = 10, totalBytes = 10),
            PackSnapshot("b", AiPackStatus.TRANSFERRING, bytesDownloaded = 10, totalBytes = 10),
        )
        assertEquals(PackUiState.Downloading(99), PackProgress.reduce(transferring, names))
    }

    @Test fun not_started_when_any_pack_needs_fetch() {
        val s = of(PackSnapshot("a", AiPackStatus.COMPLETED), PackSnapshot("b", AiPackStatus.NOT_INSTALLED))
        assertEquals(PackUiState.NotStarted, PackProgress.reduce(s, names))
    }

    @Test fun wifi_and_confirmation_states() {
        val wifi = of(PackSnapshot("a", AiPackStatus.WAITING_FOR_WIFI), PackSnapshot("b", AiPackStatus.PENDING))
        assertEquals(PackUiState.WaitingForWifi, PackProgress.reduce(wifi, names))
        val confirm = of(
            PackSnapshot("a", AiPackStatus.WAITING_FOR_WIFI),
            PackSnapshot("b", AiPackStatus.REQUIRES_USER_CONFIRMATION),
        )
        assertEquals(PackUiState.NeedsConfirmation, PackProgress.reduce(confirm, names))
    }

    @Test fun failure_wins_and_maps_storage_error() {
        val s = of(
            PackSnapshot("a", AiPackStatus.DOWNLOADING),
            PackSnapshot("b", AiPackStatus.FAILED, AiPackErrorCode.INSUFFICIENT_STORAGE),
        )
        val r = PackProgress.reduce(s, names)
        assertTrue(r is PackUiState.Failed && r.message.contains("저장 공간"))
    }
}
