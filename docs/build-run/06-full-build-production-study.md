# Full (real-Gradle) build on device — production study

You want the **full build**: the *real* Gradle + Android Gradle Plugin running on the device, so ASL
builds any project byte-for-byte like desktop Android Studio, production-grade and reliable. This is the
**android-code-studio / AndroidIDE architecture**. This document is the complete picture: how it works,
what it costs in app size, how to fix the size, and what "reliable" requires.

> TL;DR: You ship a real **OpenJDK + Android SDK + Gradle** as an on-device Linux environment and run
> genuine Gradle against it through the **Gradle Tooling API in a separate process**. It is ~100%
> faithful. The price is **~150–200 MB of toolchain per CPU architecture** — which you manage with
> **ABI splits + first-run download + on-demand SDK components**, not by shrinking the APK.

---

## 1. What "full build" actually means

A full build = **execute the real toolchain**, not reimplement it:

- **OpenJDK** (17/21) — the JVM that runs Gradle, `javac`, `kotlinc`, D8/R8. A real `java` binary.
- **Android SDK** — `android.jar` platforms, `build-tools` (`aapt2`, `d8`, `zipalign`, `apksigner`),
  `platform-tools`, `cmdline-tools`.
- **Gradle** + the **Android Gradle Plugin** — the actual build system, resolved from the project's
  `gradle-wrapper.properties`.
- A **Unix-ish environment** to host all of the above (Termux provides this: a `PREFIX/usr` tree,
  `$PATH`, shared libs, `sh`).

Because it's the real thing, **every** Gradle plugin, KSP/KAPT processor, product flavor, convention
plugin, and custom task works exactly as on a laptop. That is the entire reason to choose this path.

---

## 2. How it works — the on-device layout

android-code-studio lays out a full Linux prefix inside the app's **private data dir** (from
`Environment.java`):

```
<app files>/                          ROOT
 ├── usr/                             PREFIX  ("/usr" of a mini Linux)
 │    ├── bin/                        sh, and symlinks into the toolchain
 │    ├── lib/
 │    │   └── jvm/java-17-openjdk/    ← the bundled OpenJDK  (JAVA_HOME)
 │    ├── tmp/
 │    └── share/acside.properties
 └── home/                            HOME
      ├── .androidide/                IDE config
      ├── .gradle/                    GRADLE_USER_HOME  (downloaded deps, wrapper dists)
      └── android-sdk/                ANDROID_HOME / ANDROID_SDK_ROOT
```

The IDE exports a real environment for every build/terminal command:

```
JAVA_HOME        = <PREFIX>/lib/jvm/java-17-openjdk
ANDROID_HOME     = <HOME>/android-sdk
ANDROID_SDK_ROOT = <HOME>/android-sdk
GRADLE_USER_HOME = <HOME>/.gradle
PATH             = <PREFIX>/bin : <JAVA_HOME>/bin : …
LD_LIBRARY_PATH  = <JAVA_HOME>/lib : …
```

### How the environment gets there — the "bootstrap"
The prefix is delivered as a **bootstrap package**: a pre-built `.tar.xz`/`.zip` of `usr/` (busybox/sh,
core libs, and the OpenJDK), **built per CPU architecture** because it contains native binaries. It is
either:
- **bundled inside the APK** (android-code-studio does this — `TermuxBootstrap` notes "the bootstrap in
  the app APK added in app/build.gradle"), extracted to `PREFIX` on first launch, **or**
- **downloaded on first run** from a release URL and then extracted.

The **Android SDK, build-tools, and Gradle distributions are NOT in the APK** — they're pulled on demand
into `android-sdk/` and `.gradle/` (SDK Manager + the Gradle wrapper).

---

## 3. How a build runs, end to end

```
User taps Run
   │
   ▼
1. Ensure environment ready
     - bootstrap extracted?  JDK present?  SDK platform + build-tools for compileSdk installed?
     - if not → install/download them first (SDK Manager, one-time per version)
   │
   ▼
2. Start / reuse the Tooling API server  (SEPARATE PROCESS)
     - a headless JVM launched with the bundled OpenJDK
     - hosts a GradleConnector; the IDE talks to it over a socket (IToolingApiServer / IToolingApiClient)
   │
   ▼
3. GradleConnector.newConnector()
       .forProjectDirectory(projectDir)
       .useGradleVersion(...) or useInstallation(gradleHome)   // from gradle-wrapper.properties
       .connect()
     + inject an init-script plugin (AndroidIDEInitScriptPlugin) that:
         - streams logs/events back (LogSenderPlugin)
         - exposes the sync model (modules, variants, dependencies)
   │
   ▼
4. SYNC:  connection.model(...) / a custom model builder → read the project structure
          (builder-model-impl mirrors AGP's DefaultVariant / DefaultAndroidArtifact / DefaultLibrary)
   │
   ▼
5. BUILD:  BuildLauncher.forTasks(":app:assembleDebug")
             .setStandardOutput/Error(streamed to the IDE console)
             .withCancellationToken(...)                       // real cancellation
             .run()
           → real Gradle runs: aapt2, kotlinc, javac, D8/R8, zipalign, apksigner
   │
   ▼
6. INSTALL + LAUNCH:  PackageInstaller installs app-debug.apk, Intent launches the activity
```

The Tooling API server lives in **its own OS process** because Gradle needs a large heap and its own
classloader world; isolating it means a Gradle OOM/crash doesn't take down the editor UI.

---

## 4. Does this affect app size? — Yes, a lot. Here's the honest breakdown.

The APK itself is not what compiles code — the **toolchain** does, and the toolchain is large. Rough
real-world numbers (AndroidIDE-class app):

| Component | Size | Where it lives |
|---|---|---|
| Your IDE code + Compose UI | ~30–60 MB | APK |
| **Bootstrap (mini Linux + OpenJDK)** | **~90–150 MB per ABI** | APK **or** first-run download |
| Android SDK platform (`android.jar` per API) | ~60–90 MB each | downloaded to `android-sdk/` |
| build-tools (aapt2, d8, zipalign, apksigner) | ~50 MB per version | downloaded |
| Gradle distribution | ~130 MB per version | downloaded to `.gradle/` |
| Per-project dependencies (AndroidX, Compose…) | 100s of MB | downloaded to `.gradle/` |

So two separate "sizes":
- **Install size** = APK. If you bundle the bootstrap, the **APK is ~150–200 MB per ABI** (this is why
  android-code-studio ships **one APK per architecture** and *rejects* anything but `arm64-v8a` /
  `armeabi-v7a`). If you download the bootstrap on first run, the APK can be **~40–70 MB** but first
  launch downloads ~120 MB.
- **On-device footprint after setup** = APK + JDK + SDK + Gradle + caches, realistically **1.5–4 GB**
  once a real project has built. There is no avoiding this — it's what a real Android build needs.

**Native code = per-architecture.** The JDK and native tools are compiled for a specific ABI, so you
cannot ship one universal small binary. That fact drives every size decision below.

---

## 5. Solutions for the app-size problem (this is the real engineering)

You don't "shrink" a real toolchain — you **stop shipping it in the APK** and **fetch per-architecture,
per-version, on demand**. Options, best-first:

### 5.1 ABI splits / per-ABI APKs (do this always)
Build a separate APK per architecture so an arm64 phone never carries armeabi bytes. android-code-studio
enforces exactly `arm64-v8a` + `armeabi-v7a`. Halves the native payload immediately.
```kotlin
splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a"); isUniversalApk = false } }
```

### 5.2 Android App Bundle (AAB) → Play delivers per-device
Publish an `.aab`; Google Play generates an optimized APK per device (only that phone's ABI + density +
language). This is the **cleanest production answer** for size if you distribute on Play. (F-Droid /
sideload can't use AAB → keep the per-ABI split APKs for those channels.)

### 5.3 Download the bootstrap on first run (biggest APK win)
Ship a small APK; on first launch, download the correct-ABI bootstrap `.tar.xz` from your release server,
verify a checksum/signature, extract to `PREFIX`. APK drops to ~40–70 MB. Trade-off: first-run needs
network + a progress/verify/retry flow. **Recommended for production** — combine with 5.1/5.2.

### 5.4 On-demand SDK & Gradle (already the norm)
Never bundle the SDK/Gradle. Use an **SDK Manager** UI: the user (or the IDE, reading `compileSdk` and
`gradle-wrapper.properties`) downloads exactly the platform + build-tools + Gradle version the project
needs. Cache them under `android-sdk/` and `.gradle/`, shared across projects.

### 5.5 Play Asset Delivery / dynamic feature modules
Package the bootstrap as an **install-time or on-demand asset pack** (`.aab` asset delivery), so Play
hosts and delivers it per-ABI instead of you managing a download server. Best of 5.2 + 5.3 on Play.

### 5.6 Compression & pruning
Ship the bootstrap as **`.tar.xz`** (xz gets a JDK far smaller than zip). **Prune the JDK** with
`jlink` to only the modules Gradle/AGP need (a custom runtime image can cut a full JDK roughly in half).
Strip debug symbols from native libs.

> **Recommended production combo:** per-ABI split APKs (5.1) **or** AAB (5.2), **+** first-run bootstrap
> download with checksum verification (5.3), **+** on-demand SDK/Gradle via an SDK Manager (5.4), **+**
> an xz-compressed, `jlink`-pruned JDK (5.6). Result: a ~50–70 MB install that expands to a real
> toolchain only when the user actually opens a project.

---

## 6. What "fully reliable / production" requires

Faithful builds are the easy 80%. Production reliability is the hard 20%:

1. **Separate build process + supervision.** Run the Tooling API server in its own process
   (`android:process`), a **foreground service** with a notification (Android kills background CPU
   work). Detect death (`Binder.DeathRecipient`) → surface "build process died / OOM" and auto-restart,
   keeping the editor alive.
2. **Environment integrity checks.** On every launch verify: bootstrap extracted & versioned, JDK
   runnable (`java -version`), SDK components present for the project's `compileSdk`, licenses accepted.
   Self-heal (re-extract / re-download) instead of failing cryptically.
3. **Version negotiation.** Read the project's `gradle-wrapper.properties` and `compileSdk`/AGP version;
   install the **matching** Gradle + build-tools. A JDK/AGP/Gradle mismatch is the #1 real-world failure
   — pin a compatibility matrix and warn early.
4. **Robust download layer.** Resumable downloads, checksum + signature verification, retry with
   backoff, offline detection, a mirror/fallback. The toolchain arrives over the network; treat that as
   a first-class, failure-prone subsystem.
5. **Memory headroom.** Gradle + K2 + D8/R8 are heap-hungry. Request `largeHeap`, tune the Gradle daemon
   (`org.gradle.jvmargs=-Xmx…`, single-worker on low-RAM), and surface OOM as a clear message with a
   "reduce workers" action. Consider a device-RAM gate before enabling parallel builds.
6. **Storage management.** `.gradle` caches + SDK grow to GBs. Show usage, offer cleanup, and handle
   "disk full" gracefully.
7. **Structured build output.** Parse Gradle/compiler output into a **Problems** panel (file:line jump)
   separate from the raw log — same as doc 02 §10. Non-negotiable for a usable IDE.
8. **Cancellation & incrementality.** Wire the real Gradle `CancellationToken`; keep the Gradle daemon
   warm between builds so incremental builds are fast.
9. **Signing.** Auto-manage a **debug keystore**; provide UI to configure a **release keystore** for
   signed release builds.
10. **Permissions & storage access.** Scoped storage / `MANAGE_EXTERNAL_STORAGE` handling to read
    project files, plus install permission (`REQUEST_INSTALL_PACKAGES`) for the run step.

---

## 7. The full picture — enabling full build in ASL

Phased, each phase shippable:

### Phase A — The environment ("bootstrap") subsystem
- Decide bundle-vs-download (recommend **download**, §5.3).
- Build/obtain per-ABI bootstrap tarballs (mini-Linux + `jlink`-pruned OpenJDK 17).
- Extractor + version stamp + integrity check + self-heal. `Environment` object exposing
  `JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME/PATH` (copy android-code-studio's `Environment.java` shape).
- Enforce **ABI splits** (`arm64-v8a`, `armeabi-v7a`) in ASL's `app/build.gradle.kts`.

### Phase B — SDK & Gradle management
- An **SDK Manager** (download platforms/build-tools/cmdline-tools, accept licenses).
- Read `gradle-wrapper.properties` → ensure the right Gradle distribution.
- Download layer: resumable, checksum-verified, retryable.

### Phase C — The Tooling API server (separate process)
- A headless launcher that starts a JVM (the bundled JDK) hosting a `GradleConnector`.
- IPC (socket or AIDL) — port android-code-studio's `tooling/api` (`IToolingApiServer`,
  `IToolingApiClient`, `ToolingApiLauncher`) and `tooling/impl` (`ToolingApiServerImpl`, `Main`).
- Init-script plugin for log streaming + sync model (port `tooling/plugin` + `builder-model-impl`).

### Phase D — Sync
- `connection.model(...)` → real modules/variants/deps → back a real `ProjectRepository` (replacing the
  fakes). Now the file tree, editor, and UI designer see the real project.

### Phase E — Build & Run
- `BuildLauncher.forTasks("assembleDebug").run()` with streamed stdout/err + cancellation.
- Build console with **Problems/Log** tabs (parse output).
- `PackageInstaller` install + `Intent` launch. Debug keystore auto-managed.

### Phase F — Production hardening
- Foreground service + death detection + auto-restart (§6.1).
- Version-compatibility matrix + early warnings (§6.3).
- Storage/RAM management, release signing UI, offline handling, telemetry on build failures.

### What to port vs. build
| Concern | Action |
|---|---|
| Termux env, bootstrap, `Environment` | **Port** from android-code-studio (`termux/`, `core/common/.../Environment.java`). |
| Tooling API client/server/init-plugin/builder-model | **Port** `tooling/**` (this is the load-bearing, hard-to-write part). |
| SDK Manager, download layer | **Port/adapt** from android-code-studio's system handlers. |
| Build console, run picker, SDK Manager UI | **Build your own** in ASL's Compose design system. |

---

## 8. Full build vs. native build — pick with eyes open

| | **Full build (this doc)** | Native build (CodeAssist, doc 04B) |
|---|---|---|
| Fidelity | 100% — any Gradle project/plugin | high, but you maintain AGP behaviors |
| Install size | big, or big-first-download | small |
| On-device footprint | 1.5–4 GB after setup | 100s of MB |
| Cold build speed | slow (real Gradle daemon) | fast |
| RAM | heavy | light |
| Maintenance | low (AGP upgrades free) | high |
| Production reliability effort | high (env/version/download/process) | high (correctness parity) |
| Fits "production, real projects" | ✅ this is the reason to choose it | ⚠️ only projects it can statically import |

**Since you've chosen production + real projects over "Lite," the full build is the correct architecture.**
The dominant cost is not code — it's the **environment/toolchain/versioning/download** machinery of §5–6.
Budget for that as its own subsystem, and port android-code-studio's `termux/` + `tooling/` rather than
writing it from scratch. (Both reference projects are **GPLv3** — porting their source makes ASL's derived
modules GPLv3; confirm that fits your distribution plan before copying.)
