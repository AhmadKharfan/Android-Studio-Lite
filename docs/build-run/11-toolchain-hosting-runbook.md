# Toolchain hosting runbook (Phase 3A / T2)

The infrastructure work described in [07-termux-bootstrap-rebuild.md](07-termux-bootstrap-rebuild.md)
is implemented in the sibling workspace **`~/AndroidStudioProjects/asl-toolchain/`** (to be pushed
to `github.com/AhmadKharfan/asl-toolchain`). See its README for the full runbook; this doc records
only what the app side needs to know.

## What it produces

Per-ABI (arm64-v8a first) `.tar.xz` components rebuilt from a pinned `termux/termux-packages`
commit with `TERMUX_APP__PACKAGE_NAME=com.ahmadkharfan.androidstudiolite`, so every binary's
RUNPATH resolves inside ASL's sandbox — plus Gradle 8.7 and SDK platform-34 archives and the
manifest JSON in exactly the format [`EnvironmentManifest.kt`](../../app/src/main/java/com/ahmadkharfan/androidstudiolite/data/environment/EnvironmentManifest.kt)
parses. Pinned compat triple: **JDK 17.0.19 · Gradle 8.7 · AGP 8.5.2** (compileSdk 34).

## The value for `IDE_ENVIRONMENT_MANIFEST_URL` (full flavor only)

```
https://github.com/AhmadKharfan/asl-toolchain/releases/download/manifest/manifest.json
```

The `manifest` release tag is a stable URL whose single asset is clobber-updated by CI; artifact
URLs inside it point at immutable `toolchain-vN` releases.

## Component layout the manifest uses

| id | targetPath | note |
|---|---|---|
| `jdk-native-libs` | `usr/lib` | must precede `jdk` in the manifest — see coupling note |
| `jdk` | `usr/lib/jvm/java-17-openjdk` | JAVA_HOME |
| `gradle` | `home/gradle/gradle-8.7` | use `GradleConnector.useInstallation`, not the wrapper-dists layout |
| `android-platform-34` | `home/android-sdk/platforms/android-34` | |
| `aapt2` (later) | `home/android-sdk/build-tools/aapt2` | for `android.aapt2FromMavenOverride` |

**Coupling note:** markers only compare version strings, so the toolchain repo bumps `jdk` and
`jdk-native-libs` versions together (both read `COMPONENT_REVISION`). The extractor now *merges*
rather than wipes (see below), so a `jdk-native-libs` reinstall no longer destroys the nested
`jdk`; keeping the versions coupled is still good hygiene for clean reinstalls.

## App-side gaps found while building this — now fixed

Extraction moved into [`TarXzExtractor`](../../app/src/main/java/com/ahmadkharfan/androidstudiolite/data/environment/TarXzExtractor.kt)
(unit-tested in `TarXzExtractorTest`):

1. **Symlinks** are now created as real symlinks (`Os.symlink`, injected so JVM tests can use
   `Files.createSymbolicLink`), with targets validated to stay inside the component. Archives may
   still ship dereferenced if the packaging scripts prefer it, but the app no longer requires it.
   `$PREFIX/bin/java` is created post-install by `linkJavaLauncher` (mirrors the `asl-env-probe`
   link), so `$PREFIX/bin/java -version` works in addition to `$JAVA_HOME/bin/java -version`.
2. **Merge instead of wipe:** the extractor stages entries in a sibling `*.extract-tmp` dir, then
   moves them in without deleting the target root — nested components survive, and a rejected
   archive leaves the installed component untouched.

## Device verification (T2 milestone)

With a debuggable full-flavor build pointing at the manifest URL, complete Setup, then:

```bash
adb shell run-as com.ahmadkharfan.androidstudiolite sh -c '
  JAVA_HOME=/data/data/com.ahmadkharfan.androidstudiolite/files/usr/lib/jvm/java-17-openjdk
  $JAVA_HOME/bin/java -version'
```

Expected: `openjdk version "17.0.19"`, exit 0.
