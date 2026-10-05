package com.odom.privatediary.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun manifest_parses_models() {
        val json = """
            {"version":1,"models":[
              {"name":"ggml-small.bin","path":"whisper/ggml-small.bin","bytes":487000000,"sha256":"aa"},
              {"name":"gemma-3n-E2B-it.litertlm","path":"gemma/g.litertlm","bytes":3700000000,"sha256":"bb"}
            ]}
        """.trimIndent()
        val m = ManifestParser.parse(json)
        assertEquals(1, m.version)
        assertEquals(2, m.models.size)
        assertEquals("ggml-small.bin", m.models[0].name)
        assertEquals(3700000000L, m.models[1].bytes)
        assertEquals("bb", m.models[1].sha256)
    }

    @Test fun sha256_matches_known_vector() {
        val f = File(tmp.root, "abc.txt")
        f.writeBytes("abc".toByteArray(Charsets.US_ASCII))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex(f))
        assertTrue(Sha256.verify(f, "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
        assertFalse(Sha256.verify(f, "deadbeef"))
    }

    @Test fun download_plan_range_and_completion() {
        assertEquals("bytes=0-", DownloadPlan.rangeHeader(0))
        assertEquals("bytes=1024-", DownloadPlan.rangeHeader(1024))
        assertFalse(DownloadPlan.isComplete(5, 10))
        assertTrue(DownloadPlan.isComplete(10, 10))
        assertFalse(DownloadPlan.isComplete(0, 0))
    }

    @Test fun storage_check() {
        assertTrue(StorageCheck.hasSpaceFor(100, 200))
        assertTrue(StorageCheck.hasSpaceFor(100, 100))
        assertFalse(StorageCheck.hasSpaceFor(200, 100))
    }

    @Test fun r2_url_join_handles_slashes() {
        assertEquals("https://REPLACE-ME.r2.example/models/manifest.json", R2Config.manifestUrl())
        assertEquals("https://REPLACE-ME.r2.example/models/whisper/x.bin", R2Config.url("/whisper/x.bin"))
    }
}
