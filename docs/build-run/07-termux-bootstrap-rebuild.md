# Building ASL's own Termux-based bootstrap (the real JDK/SDK toolchain)

This documents the infrastructure work required for [SetupScreen](../../app/src/main/java/com/example/androidstudiolite/feature/onboarding/setup/SetupScreen.kt)
to install a JDK that actually **runs** on device, not just downloads and extracts successfully. It is
a one-time (plus ongoing-maintenance) build/hosting project — **not achievable by editing app source or
pointing `IDE_ENVIRONMENT_MANIFEST_URL` at a public URL.** This doc exists so whoever picks up that work
has the exact, verified mechanism instead of starting from scratch.

## Why upstream Termux packages don't work as-is

Verified directly (not assumed) on 2026-07-11 against Termux's real package repo:

- Termux publishes real, versioned `.deb` packages for `openjdk-17` per ABI. Confirmed via
  `https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages`:
  - `aarch64`: `pool/main/o/openjdk-17/openjdk-17_17.0.19_aarch64.deb`, 95,556,240 bytes,
    SHA256 `bbc12179554048df44d0afbf5cc0e4b744c26638cb0d3a905e67405cf6e05431`
  - `arm` (armeabi-v7a): `pool/main/o/openjdk-17/openjdk-17_17.0.19_arm.deb`, 92,195,816 bytes,
    SHA256 `355bc52c2f3cfbd33e830b44d234c62bb3818fa0c3863fbd15c0434bb5f346da`
- A `.deb` is an `ar` archive of `debian-binary` + `control.tar.xz` + `data.tar.xz` (verified with
  `ar t`). The payload lives under `data.tar.xz`, with every file path prefixed
  `./data/data/com.termux/files/usr/...` (verified by extracting the real `termux-tools` package).
- **The blocker:** every compiled Termux binary has that path **hardcoded as an absolute `RUNPATH`** at
  link time. Verified with `readelf -d` on the real `bash` binary from Termux's `bash` package:
  ```
  0x000000000000001d (RUNPATH)  Library runpath: [/data/data/com.termux/files/usr/lib]
  ```
  Android sandboxes every app's `/data/data/<package>/` from every other app's, so extracting this
  binary into ASL's own storage does not make that path resolve — the dynamic linker still looks for
  its shared libraries at `com.termux`'s directory, which ASL cannot read. The binary would extract and
  verify fine (Setup would show "Installed"), then fail with a linker error the first time it's
  actually executed.

## How android-code-studio actually solved this

android-code-studio does not consume upstream Termux packages. It maintains its **own rebuild** of the
entire Termux bootstrap, with the package identity substituted throughout. This is documented in their
own source, `termux/shared/.../TermuxConstants.java`:

> "The binaries compiled for termux have `TERMUX_PREFIX_DIR_PATH` hardcoded in them but it can be
> changed during compilation. The `TERMUX_PACKAGE_NAME` must be the same as the applicationId of
> termux-app build.gradle... If `TERMUX_PACKAGE_NAME` is changed, then binaries, specially used in
> bootstrap, need to be compiled appropriately. Check
> https://github.com/termux/termux-packages/wiki/Building-packages for more info."

And concretely, in the same file:
```java
public static final String TERMUX_PACKAGE_NAME = "com.tom.rv2ide"; // Default: "com.termux"
```
Every binary in their bootstrap is cross-compiled with `RUNPATH=/data/data/com.tom.rv2ide/files/usr/lib`
instead of `com.termux`'s path — which *does* resolve, because that's their own app's sandboxed
directory.

## What this means for ASL

To get a JDK (and the rest of the toolchain) that actually runs inside ASL's sandbox, matching
android-code-studio's architecture, requires:

1. **Fork `termux/termux-packages`** (the build system upstream Termux itself uses:
   https://github.com/termux/termux-packages).
2. **Rebuild the package set with ASL's package identity substituted**, following
   https://github.com/termux/termux-packages/wiki/Building-packages — the wiki page
   `TermuxConstants.java`'s comment points at directly. In practice this means rebuilding, cross-compiled
   for Android/Bionic per ABI (`arm64-v8a`, `armeabi-v7a`):
   - `openjdk-17` itself
   - its full dependency chain (per the real Termux package metadata already fetched):
     `libandroid-shmem`, `libandroid-spawn`, `libiconv`, `libjpeg-turbo`, `zlib`, `littlecms`,
     `alsa-plugins` — each of those transitively pulls its own deps
   - eventually the rest of the toolchain this same pipeline needs: Android SDK `build-tools`
     (`aapt2`, `d8`, `zipalign`, `apksigner`), `platform-tools`, and Gradle itself (Gradle is
     pure-Java/JVM, so it does **not** need this treatment — only native/compiled pieces do)
3. **Settle ASL's real `applicationId` first.** It is currently `com.ahmadkharfan.androidstudiolite`
   (`app/build.gradle.kts`) — a placeholder. Every rebuilt binary bakes this string in as an absolute
   path; changing it later means rebuilding the entire toolchain from scratch. Decide the production
   package id before investing in this build.
4. **Set up the build infrastructure.** Termux's build system runs in a provided Docker image
   (cross-compilation is resource- and time-intensive — expect hours per full rebuild, not minutes).
   This needs a CI machine or dedicated build host, not a one-off local `./build-package.sh` run.
5. **Host the output** as a manifest + per-ABI archives matching the schema
   [`EnvironmentManifest.kt`](../../app/src/main/java/com/example/androidstudiolite/data/environment/EnvironmentManifest.kt)
   already expects (id/version/targetPath/per-ABI url+sha256+size). The archive format can be a plain
   `.tar.xz` per component (what the current extractor already handles) rather than the `.deb`/`ar`
   wrapper — that wrapper only exists because Termux distributes through an apt-style repo; ASL's own
   hosting doesn't need to replicate that.
6. **Point `IDE_ENVIRONMENT_MANIFEST_URL`** (`app/build.gradle.kts`) at the hosted manifest. At that
   point the existing onboarding/install pipeline (already built and working —
   [`AndroidIdeEnvironmentRepository.kt`](../../app/src/main/java/com/example/androidstudiolite/data/environment/AndroidIdeEnvironmentRepository.kt))
   needs no further changes.

## Ongoing cost, not just one-time

This is maintenance, not a single build: JDK security updates, Android SDK/build-tools revisions, and
Gradle version bumps all mean rebuilding and re-hosting the affected component. Budget for this as a
recurring release-engineering task, matching how android-code-studio maintains their own `composite-builds`
and `.androidide_root`-rooted package set.

## What's already correct and doesn't need to change

The app-side pipeline is already built for exactly this outcome — it just needs real, sandbox-runnable
archives behind the manifest:
- [`IdeEnvironmentPaths.kt`](../../app/src/main/java/com/example/androidstudiolite/core/environment/IdeEnvironmentPaths.kt) —
  correct on-device layout (`usr/lib/jvm/...`, `home/android-sdk`, `home/.gradle`).
- [`AndroidIdeEnvironmentRepository.kt`](../../app/src/main/java/com/example/androidstudiolite/data/environment/AndroidIdeEnvironmentRepository.kt) —
  resumable download, SHA-256 verification, `.tar.xz` extraction with a zip-slip guard, exec-bit
  preservation, atomic marker files.
- The Setup and Settings → IDE Configuration screens both already render real per-component progress and
  failure states from this same repository.

No app code needs to change once real, correctly-linked archives exist at the manifest URL.
