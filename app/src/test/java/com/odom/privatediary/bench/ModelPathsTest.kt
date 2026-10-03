package com.odom.privatediary.bench

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
        File(tmp.root, "ggml-small.bin").writeText("x")
        File(tmp.root, "gemma-3n-E2B-it.litertlm").writeText("y")
        val p = ModelPaths(tmp.root)
        assertTrue(p.whisperReady())
        assertTrue(p.gemmaReady())
    }
}
