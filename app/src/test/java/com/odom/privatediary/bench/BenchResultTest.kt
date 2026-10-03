package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BenchResultTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun csv_row_formats_and_sanitizes_commas() {
        val row = BenchRecord("gemma", "rec01", 1234, 567, true, "a,b").toCsvRow()
        assertEquals("gemma,rec01,1234,567,true,a;b", row)
    }

    @Test fun append_writes_header_once_then_rows() {
        val csv = File(tmp.root, "bench.csv")
        BenchRecord("whisper", "rec01", 10, 20, true).appendTo(csv)
        BenchRecord("gemma", "rec01", 30, 40, false).appendTo(csv)
        val lines = csv.readLines()
        assertEquals(BenchRecord.CSV_HEADER, lines[0])
        assertEquals("whisper,rec01,10,20,true,", lines[1])
        assertEquals("gemma,rec01,30,40,false,", lines[2])
    }
}
