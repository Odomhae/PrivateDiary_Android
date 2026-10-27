package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelPathsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun not_ready_when_files_absent() {
        val p = ModelPaths(tmp.root)
        assertFalse(p.whisperReady())
        assertFalse(p.gemmaReady())
    }

    @Test fun ready_when_nonempty_files_present() {
        File(tmp.root, ModelPaths.WHISPER_FILE).writeText("x")
        File(tmp.root, ModelPaths.GEMMA_FILE).writeText("y")
        val p = ModelPaths(tmp.root)
        assertTrue(p.whisperReady())
        assertTrue(p.gemmaReady())
    }

    @Test fun resolve_prefers_pack_dir_when_file_present() {
        val pack = tmp.newFolder("pack")
        val side = tmp.newFolder("side")
        File(pack, ModelPaths.GEMMA_FILE).writeText("g")
        assertEquals(pack, ModelPaths.resolveDir(pack, side, ModelPaths.GEMMA_FILE))
    }

    @Test fun resolve_falls_back_to_sideload_when_pack_missing_or_empty() {
        val pack = tmp.newFolder("pack")
        val side = tmp.newFolder("side")
        assertEquals(side, ModelPaths.resolveDir(null, side, ModelPaths.GEMMA_FILE))
        assertEquals(side, ModelPaths.resolveDir(pack, side, ModelPaths.GEMMA_FILE))
    }
}
