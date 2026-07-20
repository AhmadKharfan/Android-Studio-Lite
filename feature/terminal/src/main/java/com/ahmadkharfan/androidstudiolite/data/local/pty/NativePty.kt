package com.ahmadkharfan.androidstudiolite.data.local.pty

internal object NativePty {

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("asl_pty")
            loaded = true
        }
    }

    external fun nativeForkPty(argv: Array<String>, envp: Array<String>, cwd: String?, rows: Int, cols: Int): IntArray?

    external fun nativeSetWinSize(fd: Int, rows: Int, cols: Int)

    external fun nativeWaitFor(pid: Int): Int

    external fun nativeDestroy(fd: Int, pid: Int)
}
