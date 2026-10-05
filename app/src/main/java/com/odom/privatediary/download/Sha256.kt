package com.odom.privatediary.download

import java.io.File
import java.security.MessageDigest

object Sha256 {
    fun hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedHex: String): Boolean = hex(file).equals(expectedHex, ignoreCase = true)
}
