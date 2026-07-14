package com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight

import android.os.StatFs
import java.io.File

/** Reads real free space on the filesystem backing the build (the app's private storage). */
object DeviceStorage {

    fun availableBytes(dir: File): Long = runCatching {
        val stat = StatFs(dir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE) // On error, don't block the build on a bogus low reading.
}
