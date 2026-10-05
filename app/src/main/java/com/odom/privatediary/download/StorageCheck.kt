package com.odom.privatediary.download

object StorageCheck {
    fun hasSpaceFor(requiredBytes: Long, freeBytes: Long): Boolean = freeBytes >= requiredBytes
}
