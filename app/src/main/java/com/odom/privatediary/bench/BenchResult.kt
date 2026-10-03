package com.odom.privatediary.bench

import java.io.File

data class BenchRecord(
    val model: String,
    val inputId: String,
    val latencyMs: Long,
    val pssKb: Long,
    val ok: Boolean,
    val note: String = "",
) {
    fun toCsvRow(): String = listOf(
        model, inputId, latencyMs.toString(), pssKb.toString(), ok.toString(),
        note.replace(",", ";"),
    ).joinToString(",")

    companion object {
        const val CSV_HEADER = "model,inputId,latencyMs,pssKb,ok,note"
    }
}

fun BenchRecord.appendTo(csv: File) {
    if (!csv.exists()) csv.writeText(BenchRecord.CSV_HEADER + "\n")
    csv.appendText(toCsvRow() + "\n")
}
