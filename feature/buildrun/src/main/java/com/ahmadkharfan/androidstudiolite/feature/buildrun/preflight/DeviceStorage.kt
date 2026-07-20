package com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight

import android.os.StatFs
import java.io.File

object DeviceStorage {

    fun availableBytes(dir: File): Long = runCatching {
        val stat = StatFs(dir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE)
}
