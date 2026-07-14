package com.ahmadkharfan.androidstudiolite.data.local.pty

/**
 * Thin JNI bridge to the `asl_pty` native shim (see `app/src/main/cpp/asl_pty.c`). All methods map
 * one-to-one onto C functions; higher-level lifecycle and stream handling live in [PtyProcess].
 */
internal object NativePty {

    @Volatile
    private var loaded = false

    /** Loads `libasl_pty.so` on first use; throws [UnsatisfiedLinkError] if the ABI has no build. */
    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("asl_pty")
            loaded = true
        }
    }

    /** Fork a shell under a new PTY. Returns `{ masterFd, pid }`, or null if `forkpty` failed. */
    external fun nativeForkPty(argv: Array<String>, envp: Array<String>, cwd: String?, rows: Int, cols: Int): IntArray?

    /** Push a new window size to the kernel (delivers SIGWINCH to the child). */
    external fun nativeSetWinSize(fd: Int, rows: Int, cols: Int)

    /** Block until the child exits; returns its exit code (128+signal if killed), or -1. */
    external fun nativeWaitFor(pid: Int): Int

    /** SIGHUP the child and close the master fd. */
    external fun nativeDestroy(fd: Int, pid: Int)
}
