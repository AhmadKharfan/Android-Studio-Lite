# Third-Party Notices

Android Studio Lite is proprietary software (see [LICENSE](LICENSE)). It incorporates, or will
incorporate as development proceeds, the following third-party components under their own
licenses. This file must be kept current as each component is actually added.

## Policy

- **No GPL code is incorporated.** GPL-licensed projects (android-code-studio, Termux,
  CodeAssist) are architectural references only; all implementation in this repository is
  written fresh against permissively-licensed components.
- **This covers assets, not just code.** Artwork, icons and layouts from those projects are not
  copied either. Where ASL deliberately matches their UX — e.g. the create-project template
  picker uses android-code-studio's template names and ordering so users can move between the
  two IDEs — the *arrangement* is matched, while the artwork (`res/drawable/template_*.xml`) is
  drawn fresh for ASL, and each template's generated code is written fresh against the public
  AndroidX/Material APIs.
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
| Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar` from Gradle 9.4.1) | Apache-2.0 | Shipped in `app/src/main/assets/wrapper/` and copied verbatim into every generated project, which pins its own Gradle distribution (8.7) via `gradle-wrapper.properties`. The jar is only a bootstrapper (~45 KB, Java 8 bytecode) — it downloads and runs the pinned distribution on the build server. |

Runtime-downloaded toolchain (full flavor, separate processes, never linked):

| Component | License | Use |
|-----------|---------|-----|
| OpenJDK | GPLv2 + Classpath Exception | Runs Gradle and the tooling server on-device |
| Gradle distribution | Apache-2.0 | Real project builds on-device |
| Android SDK components (platform, build-tools) | AOSP / Android SDK terms | Build inputs |
