// ASL's own JNI pseudo-terminal shim — no GPL terminal-emulator code was consulted or copied. It wraps
// bionic's forkpty(3) so the app can run a real interactive shell (curses apps, job control, line
// editing) behind a master PTY fd, which Kotlin drives through a ParcelFileDescriptor.
//
// forkpty() opens a master/slave PTY pair, forks, and in the child makes the slave the controlling
// terminal and dup2()s it onto stdin/stdout/stderr. We hand back the master fd and child pid.
//
// Fork-safety: everything that touches the JVM (extracting the Java string arrays) happens BEFORE the
// fork. The child path calls only async-signal-safe libc (execve/_exit), never JNI.

#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <signal.h>

// Copy a Java String[] into a NULL-terminated char** (must be freed with free_cstr_array).
static char **to_cstr_array(JNIEnv *env, jobjectArray array) {
    jsize n = (*env)->GetArrayLength(env, array);
    char **out = (char **) calloc((size_t) n + 1, sizeof(char *));
    if (out == NULL) return NULL;
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        const char *utf = (*env)->GetStringUTFChars(env, s, NULL);
        out[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, s, utf);
        (*env)->DeleteLocalRef(env, s);
    }
    out[n] = NULL;
    return out;
}

static void free_cstr_array(char **array) {
    if (array == NULL) return;
    for (char **p = array; *p != NULL; p++) free(*p);
    free(array);
}

/*
 * Fork a child running argv[0] (an absolute path) under a fresh PTY of size rows x cols, with the
 * given environment. Returns a two-element int[] { masterFd, pid }, or null on failure.
 */
JNIEXPORT jintArray JNICALL
Java_com_ahmadkharfan_androidstudiolite_data_local_pty_NativePty_nativeForkPty(
        JNIEnv *env, jclass clazz,
        jobjectArray jargv, jobjectArray jenvp, jstring jcwd, jint rows, jint cols) {
    (void) clazz;

    char **argv = to_cstr_array(env, jargv);
    char **envp = to_cstr_array(env, jenvp);
    if (argv == NULL || envp == NULL || argv[0] == NULL) {
        free_cstr_array(argv);
        free_cstr_array(envp);
        return NULL;
    }
    // Extract the working directory before the fork (no JNI is allowed in the child).
    char *cwd = NULL;
    if (jcwd != NULL) {
        const char *utf = (*env)->GetStringUTFChars(env, jcwd, NULL);
        cwd = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, jcwd, utf);
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);

    int master = -1;
    pid_t pid = forkpty(&master, NULL, NULL, &ws);
    if (pid < 0) {
        free_cstr_array(argv);
        free_cstr_array(envp);
        free(cwd);
        return NULL;
    }

    if (pid == 0) {
        // Child: only async-signal-safe calls from here on. Replace ourselves with the shell.
        if (cwd != NULL) { if (chdir(cwd) != 0) { /* fall through and start in the inherited dir */ } }
        execve(argv[0], argv, envp);
        _exit(127); // execve only returns on failure
    }

    // Parent.
    free_cstr_array(argv);
    free_cstr_array(envp);
    free(cwd);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        close(master);
        return NULL;
    }
    jint vals[2] = {master, (jint) pid};
    (*env)->SetIntArrayRegion(env, result, 0, 2, vals);
    return result;
}

/* Tell the kernel the terminal was resized so the child receives SIGWINCH and reflows. */
JNIEXPORT void JNICALL
Java_com_ahmadkharfan_androidstudiolite_data_local_pty_NativePty_nativeSetWinSize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    (void) env;
    (void) clazz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ioctl(fd, TIOCSWINSZ, &ws);
}

/* Reap the child; returns its exit code (or 128+signal), or -1 if it isn't our child / already reaped. */
JNIEXPORT jint JNICALL
Java_com_ahmadkharfan_androidstudiolite_data_local_pty_NativePty_nativeWaitFor(
        JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status = 0;
    pid_t r;
    do {
        r = waitpid((pid_t) pid, &status, 0);
    } while (r < 0 && errno == EINTR);
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

/* Send SIGHUP then close the master, ending the session. */
JNIEXPORT void JNICALL
Java_com_ahmadkharfan_androidstudiolite_data_local_pty_NativePty_nativeDestroy(
        JNIEnv *env, jclass clazz, jint fd, jint pid) {
    (void) env;
    (void) clazz;
    if (pid > 0) kill((pid_t) pid, SIGHUP);
    if (fd >= 0) close(fd);
}
