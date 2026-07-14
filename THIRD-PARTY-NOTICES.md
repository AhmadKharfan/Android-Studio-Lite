# Third-Party Notices

Android Studio Lite is proprietary software (see [LICENSE](LICENSE)). It incorporates, or will
incorporate as development proceeds, the following third-party components under their own
licenses. This file must be kept current as each component is actually added.

## Policy

- **No GPL code is incorporated.** GPL-licensed projects (android-code-studio, Termux,
  CodeAssist) are architectural references only; all implementation in this repository is
  written fresh against permissively-licensed components.
- GPL-licensed toolchain *binaries* (e.g. OpenJDK, GPLv2 + Classpath Exception) are downloaded
  at runtime and executed as separate processes — mere aggregation; they are never linked into
  the app.

## Components (as they are added)

| Component | License | Use |
|-----------|---------|-----|
| Eclipse Compiler for Java (ECJ) | EPL-2.0 | In-process Java compilation (play flavor) |
| Gradle Tooling API | Apache-2.0 | Real Gradle builds via tooling server (full flavor) |
| Kotlin compiler (embeddable) | Apache-2.0 | In-process Kotlin compilation (play flavor) |
| D8 / R8 (AOSP) | Apache-2.0 | Dexing / shrinking (play flavor) |
| apksig (AOSP) | Apache-2.0 | APK signing (play flavor) |
| Bouncy Castle (bcpkix/bcprov) | Bouncy Castle Licence (MIT-style) | X.509 self-signed debug-certificate generation (play flavor) |
| aapt2 (AOSP) | Apache-2.0 | Resource compilation/linking (binary shipped in jniLibs) |
| bundletool | Apache-2.0 | AAB generation and validation |
| JGit | EDL (BSD-3-Clause) | Git operations |

Runtime-downloaded toolchain (full flavor, separate processes, never linked):

| Component | License | Use |
|-----------|---------|-----|
| OpenJDK | GPLv2 + Classpath Exception | Runs Gradle and the tooling server on-device |
| Gradle distribution | Apache-2.0 | Real project builds on-device |
| Android SDK components (platform, build-tools) | AOSP / Android SDK terms | Build inputs |
