/*
 * ASL on-device environment probe.
 *
 * This is a REAL native executable, cross-compiled per-ABI by the NDK, shipped inside the APK as
 * `lib*.so` in jniLibs so the package installer extracts it into nativeLibraryDir — the only location
 * an Android app may execute a binary from on API 29+ (the app data dir is blocked by W^X unless
 * targetSdk <= 28). See docs/build-run/06-full-build-production-study.md.
 *
 * It exists to prove, end to end on a real device, that ASL can lay out an on-device toolchain prefix
 * and execute native binaries from it — the mechanism the full JDK/SDK/Gradle bootstrap will reuse. It
 * links against nothing but Bionic libc, so it runs with no library-path setup at all.
 *
 * Built as an executable (not a library) but named lib*.so; see CMakeLists.txt.
 */
#include <stdio.h>
#include <unistd.h>

#if defined(__x86_64__)
#define ASL_ABI "x86_64"
#elif defined(__i386__)
#define ASL_ABI "x86"
#elif defined(__aarch64__)
#define ASL_ABI "arm64-v8a"
#elif defined(__arm__)
#define ASL_ABI "armeabi-v7a"
#else
#define ASL_ABI "unknown"
#endif

int main(int argc, char **argv) {
    /* A single, stable line the installer parses to confirm on-device execution works. */
    printf("ASL_ENV_PROBE ok abi=%s pid=%d\n", ASL_ABI, (int) getpid());
    fflush(stdout);
    return 0;
}
