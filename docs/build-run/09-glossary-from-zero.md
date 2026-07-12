# Glossary from zero — every term, why it exists, what it does

This explains every concept behind ASL's on-device build, starting from nothing. Each entry answers:
**what it is**, **why we need it**, **what it does**, and **where it shows up in ASL**. Read top to
bottom the first time — later terms build on earlier ones.

---

## A. The core problem

### What "building an app" actually means
A program you write is **text** (`.kt` / `.java` files). A phone's CPU cannot run text. "Building" is the
chain of translations that turns your text into something the device can install and execute:

```
your source text  →  compile to bytecode  →  convert to Android's dex  →  package + sign  →  installable APK
```

Every term below is a piece of that chain, or a piece of the *machinery* that runs the chain **on the
phone itself** (which is ASL's whole point — normally this runs on a laptop).

### On-device vs. on-a-laptop
Android Studio on a laptop has a full operating system, a JDK, the Android SDK, and Gradle already
installed. A phone has none of that in a form apps can use. So ASL must **carry or fetch its own copy of
the whole toolchain** and run it inside its own app sandbox. That single fact is the source of ~80% of the
difficulty and most of the terms here.

---

## B. The Java / JVM world

### Bytecode
- **What:** an intermediate machine-independent instruction format (`.class` files).
- **Why:** so the same compiled code runs on any CPU (ARM, x86) — "write once, run anywhere."
- **What it does:** it's the output of the Java/Kotlin compiler and the input to the next stage.

### JVM (Java Virtual Machine)
- **What:** a program that executes bytecode.
- **Why:** bytecode isn't CPU instructions; something must interpret/translate it at runtime.
- **On Android:** the phone's JVM is **ART** (Android Runtime), which runs **dex**, not `.class` — hence
  the extra dex step below.

### JDK (Java Development Kit)
- **What:** the toolbox for Java development: the **`javac`** compiler, the **`java`** launcher, core
  libraries, and tools like `jar`. (A **JRE** is just the runtime half; a JDK includes the compiler.)
- **Why:** you cannot compile Java (or run Gradle, or run `kotlinc`) without a JDK. Gradle itself is a
  Java program, so it needs a JDK just to start.
- **What it does in ASL:** the on-device toolchain must include a real JDK so builds can compile code and
  run Gradle. This is the ~100 MB piece that needs the Termux rebuild (doc 07).
- **In ASL:** `JAVA_HOME` in `IdeEnvironmentPaths.kt` points at where the JDK will live.

### Kotlin compiler (`kotlinc` / K2)
- **What:** the compiler that turns `.kt` into bytecode. K2 is the modern version.
- **Why:** Android apps are mostly Kotlin now; it can be run as a library inside another JVM process.

### Classpath
- **What:** the ordered list of compiled libraries the compiler/runtime is allowed to use.
- **Why:** your code calls into libraries (AndroidX, Compose…); the compiler must know where to find them.
- **What it does:** getting it right (correct versions, no duplicates) is the hard part of an IDE.

---

## C. The Android build tools

### Android SDK (Software Development Kit)
- **What:** Google's Android-specific tools + the **platform** (`android.jar`, the API stubs for each
  Android version) + **build-tools** (see below).
- **Why:** Java tools alone can't make an APK; Android needs its own resource/dex/packaging tools.
- **In ASL:** `ANDROID_HOME` / `ANDROID_SDK_ROOT` in `IdeEnvironmentPaths.kt`.

### Gradle
- **What:** the build system that orchestrates everything — reads your `build.gradle`, downloads
  dependencies, and runs the tools in the right order.
- **Why:** a real Android build is hundreds of steps; Gradle + the Android Gradle Plugin encodes all of
  them. ASL's "full build" strategy = run the *real* Gradle on device for 100% fidelity.
- **`GRADLE_USER_HOME`:** where Gradle caches downloaded dependencies and its own distributions.

### AGP (Android Gradle Plugin)
- **What:** the plugin that teaches Gradle how to build Android specifically (resources, dex, APK…).
- **Why:** Gradle by itself only knows generic JVM builds; AGP adds all the Android knowledge.

### aapt2 (Android Asset Packaging Tool 2)
- **What:** the tool that compiles resources (`res/`, `AndroidManifest.xml`) and generates the **R class**.
- **Why:** Android resources (layouts, strings, images) must be compiled into a binary table and given
  integer IDs. It's a **native binary** (can't be pure Java) — one reason on-device builds are hard.

### The R class
- **What:** auto-generated code mapping every resource name to an int (`R.string.app_name` → `2131…`).
- **Why:** code references resources by these IDs; something must generate them before compiling code.

### dex, D8, R8
- **What:** **dex** = Dalvik Executable, the format ART runs. **D8** converts `.class` → `.dex`. **R8** is
  D8 plus shrinking/optimizing/obfuscating (removing unused code, renaming).
- **Why:** ART runs dex, not JVM bytecode, so every build must convert. R8 makes release APKs smaller.

### zipalign & apksigner
- **What:** `zipalign` aligns the APK's data for efficient memory-mapping; `apksigner` cryptographically
  signs it.
- **Why:** Android refuses to install an **unsigned** APK; signing proves who built it. A "debug" build
  is signed with an auto-generated debug key.

### APK (Android Package)
- **What:** the final installable file — a zip containing dex, resources, native libs, and the manifest.

### Dependency resolution
- **What:** turning `implementation("androidx.core:core-ktx:1.12.0")` into actual downloaded `.jar`/`.aar`
  files plus everything *they* depend on.
- **Why:** apps use dozens of libraries with their own sub-dependencies; the build must fetch and
  de-duplicate the whole tree (keeping one version of each — "newest wins").

### Task DAG & incremental build
- **What:** a build is a **graph of small tasks** (compile, dex, package…), each with declared inputs and
  outputs. **Incremental** = re-run only the tasks whose inputs changed.
- **Why:** rebuilding everything on every edit is unusably slow on a phone.

---

## D. The native / CPU layer (why binaries are ABI-specific)

### CPU architecture & ABI (Application Binary Interface)
- **What:** phones use different CPU families — **arm64-v8a** (most real phones), **armeabi-v7a** (older
  32-bit), **x86_64** / **x86** (emulators). An ABI is the exact calling convention/binary format for one.
- **Why it matters:** a native binary compiled for arm64 **will not run** on x86_64 and vice-versa. So the
  toolchain must ship a separate copy **per ABI**. Your emulator is **x86_64**.
- **In ASL:** `IdeEnvironmentPaths.deviceAbi()` detects it; the probe is built for all four.

### Native code vs. bytecode
- **What:** "native" = real CPU instructions (C/C++ compiled to an ELF binary), as opposed to
  machine-independent bytecode.
- **Why:** the JDK, aapt2, etc. are native programs — they must be compiled for the device's exact ABI,
  which is why you can't just download a generic Linux JDK.

### ELF (Executable and Linkable Format)
- **What:** the file format of executables and shared libraries on Linux/Android.
- **Why relevant:** we inspect ELF headers (`readelf`) to learn a binary's ABI, its **interpreter**, and
  its **RUNPATH** — the fields that decide whether a binary can run in ASL's sandbox.

### NDK (Native Development Kit)
- **What:** Google's toolkit for compiling C/C++ into Android native binaries, per-ABI.
- **Why:** to produce the env-probe binary (and in principle other native tools), you need a cross-compiler
  that targets each Android ABI. **ASL uses NDK 27** to build the probe.

### CMake
- **What:** a build tool for C/C++ that the Android Gradle plugin drives via `externalNativeBuild`.
- **Why:** it tells the NDK how to compile our `asl_env_probe.c` into `libasl_env_probe.so`.
- **In ASL:** `app/src/main/cpp/CMakeLists.txt`.

### PIE (Position-Independent Executable)
- **What:** an executable that can be loaded at any memory address.
- **Why:** modern Android *requires* executables to be PIE. The probe is built `-pie`.

### libc, Bionic vs glibc
- **What:** **libc** is the C standard library every native program links against. Desktop Linux uses
  **glibc**; **Android uses Bionic** (a different, smaller libc).
- **Why it's a blocker:** a JDK compiled for desktop Linux links **glibc** and simply cannot run on
  Android, which only has **Bionic**. This is *the* reason you can't drop a normal Linux JDK onto a phone —
  it must be compiled against Bionic (what Termux does).

### Dynamic linker & interpreter
- **What:** when a program starts, the **dynamic linker** (`/system/bin/linker64` on Android) loads the
  shared libraries it needs. The path to it is baked into the ELF as the "interpreter."
- **Why relevant:** if the interpreter path or the libraries can't be found, the program won't even start.

### RUNPATH / RPATH & LD_LIBRARY_PATH
- **What:** **RUNPATH** is a list of directories, baked into a binary at compile time, where the linker
  looks for that binary's libraries. **LD_LIBRARY_PATH** is an environment variable that does the same at
  runtime, and (for RUNPATH) is searched *first*.
- **Why it's the Termux blocker:** every upstream Termux binary has `RUNPATH=/data/data/com.termux/…`
  baked in. Android isolates each app's `/data/data/<package>/` from every other app, so that path can't
  resolve inside ASL's sandbox → the binary can't find its libraries → it fails. Two fixes: rebuild the
  binaries with ASL's own path baked in (the proper solution, doc 07), or override with `LD_LIBRARY_PATH`
  at runtime (a partial workaround; `IdeEnvironment.kt` already sets it).

---

## E. Android runtime constraints (the sandbox rules)

### App sandbox & private storage
- **What:** every Android app gets a private directory (`/data/data/<package>/`, exposed as `filesDir`)
  that no other app can read. This is where ASL puts the toolchain.
- **Why it matters:** because it's per-app, binaries hard-coded to *another* app's path (Termux) break.

### nativeLibraryDir
- **What:** a special read-only sub-directory of the app where the OS puts the app's native `.so`
  libraries at install time.
- **Why it's crucial:** it is the **one place an app is allowed to execute a binary from** on modern
  Android (see W^X). android-code-studio (and ASL) exploit this by shipping executables named `lib*.so`
  so they land here. **This is how ASL's probe actually runs.**

### W^X ("write XOR execute") / exec restriction
- **What:** since Android 10 (API 29), apps whose `targetSdk ≥ 29` **cannot execute files from their
  writable data directory** (`filesDir`). They *can* execute from the read-only `nativeLibraryDir`.
- **Why it matters:** a JDK extracted into `filesDir` won't run under `targetSdk ≥ 29`. Two options:
  set **`targetSdk = 28`** (what Termux/AndroidIDE do), or run binaries from `nativeLibraryDir`. This is
  the key open decision for the *full* JDK (doc 10).

### minSdk, targetSdk, compileSdk
- **What:** **minSdk** = oldest Android version the app installs on. **targetSdk** = the Android version
  the app says it's tuned for (controls behavior/restrictions like W^X). **compileSdk** = the API version
  used to *compile* against.
- **Why relevant:** `targetSdk` directly controls whether on-device exec-from-data works.
- **In ASL:** currently minSdk 24, targetSdk 36, compileSdk 37.

---

## F. The on-device toolchain (Termux, bootstrap, prefix, env)

### Termux
- **What:** an app that runs a full Linux userland on Android, with its own package manager and
  **binaries compiled against Bionic** for each ABI.
- **Why it matters:** it's the only mature source of Android-native builds of a JDK, aapt2, etc. Both
  reference IDEs build on Termux's package system.

### Bootstrap
- **What:** a pre-built archive of the minimal environment (a shell, core libs, sometimes the JDK) that an
  app extracts on first launch to set up its Linux prefix.
- **Why:** you can't compile the toolchain on the phone; you ship it pre-built and just unpack it.
- **How android-code-studio ships it:** as `libtermux-bootstrap.so` in `jniLibs` (so it lands in the
  exec-safe `nativeLibraryDir`), loaded via a tiny JNI function and extracted with symlink handling.

### Prefix
- **What:** the root of the on-device Linux environment — conventionally `…/usr` with `bin/`, `lib/`, etc.
- **Why:** all the toolchain binaries expect a Unix-like `$PREFIX` layout.
- **In ASL:** `IdeEnvironmentPaths.prefix()` = `filesDir/usr`.

### Environment variables (JAVA_HOME, ANDROID_HOME, PATH, LD_LIBRARY_PATH, PREFIX, TMPDIR)
- **What:** named values a process inherits that tell tools where things are. `JAVA_HOME` = the JDK,
  `ANDROID_HOME` = the SDK, `PATH` = where to find executables, `LD_LIBRARY_PATH` = where to find shared
  libraries, `PREFIX` = the environment root, `TMPDIR` = scratch space.
- **Why:** Gradle/`java`/`aapt2` won't work unless these point at the right on-device locations.
- **In ASL:** `IdeEnvironment.environment()` builds them.

---

## G. Distribution & reliability plumbing

### Manifest (the environment manifest)
- **What:** a JSON file listing each toolchain component: id, version, where it installs, and — per ABI —
  a download URL, a checksum, and a size.
- **Why:** so the app knows *what* to download and *how to verify* it, without hard-coding it. Updating the
  toolchain = updating the manifest, not the app.
- **In ASL:** `EnvironmentManifest.kt`; the URL is `BuildConfig.IDE_ENVIRONMENT_MANIFEST_URL` (currently
  empty → the app installs only the bundled runtime).

### Checksum / SHA-256
- **What:** a fixed-length fingerprint computed from a file's bytes; if one byte differs, the fingerprint
  differs.
- **Why:** to prove a 100 MB download arrived intact and wasn't corrupted or tampered with before you
  extract and execute it. ASL verifies every download's SHA-256 before use.

### tar.xz
- **What:** **tar** bundles many files into one; **xz** compresses it (very high ratio, good for a JDK).
- **Why:** ship the toolchain as one small file. ASL extracts it with commons-compress + the xz library.

### Resumable download
- **What:** if a big download is interrupted, continue from where it stopped (via HTTP `Range`) instead of
  restarting.
- **Why:** phones drop connections; re-downloading 100 MB each time is unacceptable.

### Symlink (symbolic link)
- **What:** a file that points at another path; opening/exec'ing it transparently uses the target.
- **Why here:** the probe must physically live in `nativeLibraryDir` (exec-safe), but the toolchain layout
  wants it at `usr/bin/…`. A symlink at `usr/bin/asl-env-probe` → the real `.so` bridges the two.

### Marker file & atomic write
- **What:** a small file written **only after** a component is fully, correctly installed, recording its
  version. **Atomic write** = write to a temp file then rename, so a crash never leaves a half-written file.
- **Why:** so the app knows what's already installed (skip re-installing) and can't be fooled by a partial
  install. ASL writes `installed-<id>.properties` after verification passes.

---

## H. Permissions

### Runtime permission vs. special ("settings-screen") permission
- **What:** most permissions are granted by a **popup dialog** (runtime). A few powerful ones can only be
  granted by sending the user to a **Settings screen** to flip a toggle.
- **Why two kinds:** Android treats broad, risky permissions differently.
- **In ASL:** `AslPermissions.kt` models both (`Runtime` vs `SettingsScreen`).

### Storage access (READ/WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE)
- **What:** permission to read/write files outside the app's sandbox. On Android ≤ 10 it's a runtime
  permission; on 11+ broad access is the special **"All files access"** (`MANAGE_EXTERNAL_STORAGE`).
- **Why ASL needs it:** to open, edit, and build project files the user picks anywhere on the device.

### REQUEST_INSTALL_PACKAGES
- **What:** permission to install an APK from within the app (a special Settings-screen permission).
- **Why:** the whole point of the IDE is to build an APK and **install/run** it — this permission enables
  the "Run" step.

### POST_NOTIFICATIONS
- **What:** (API 33+) permission to show notifications.
- **Why:** to notify when a long background build finishes.

### Foreground service
- **What:** a service that keeps running (with an ongoing notification) even when the app isn't in front.
- **Why:** Android kills background CPU work; a long build/Gradle daemon must be a foreground service to
  survive the user switching away. ASL declares `FOREGROUND_SERVICE` for this.

---

## I. App architecture terms (how the code is organized)

### Repository (pattern)
- **What:** a class that owns access to one kind of data/behavior behind an interface (e.g.
  `OnboardingRepository`, `IdeEnvironmentRepository`).
- **Why:** the UI depends on the *interface*, so a fake can be swapped for a real implementation. That's
  exactly what this work did — replace fakes with real device-backed implementations.

### ViewModel
- **What:** holds a screen's state and handles its logic, surviving screen rotation.
- **Why:** keeps UI code (Compose) thin and testable.

### Jetpack Compose
- **What:** Android's modern declarative UI toolkit (the `@Composable` screens).
- **Why:** it's what ASL's UI is written in.

### Koin (dependency injection / DI)
- **What:** a library that supplies each class its dependencies (which repository, etc.) from one place
  (`KoinModules.kt`).
- **Why:** so wiring "use the *real* repo, not the fake" happens in one line, not scattered everywhere.

### DataStore
- **What:** Android's modern key-value persistence (replacement for SharedPreferences).
- **Why:** to remember flags like "onboarding complete" across app restarts. Used by
  `AndroidOnboardingRepository`.

### Coroutine / Flow
- **What:** Kotlin's tools for background work (**coroutine**) and streams of values over time (**Flow**).
- **Why:** downloads/installs run off the UI thread and stream progress back to the screen live.

### ProcessBuilder / exec
- **What:** the Java/Kotlin way to launch another program as a child process and read its output.
- **Why:** it's how ASL actually runs the native probe (and, later, `java`/`gradle`).
- **In ASL:** `IdeEnvironment.run()`.

### JNI (Java Native Interface)
- **What:** the bridge that lets Java/Kotlin call C/C++ code.
- **Why relevant:** android-code-studio uses a tiny JNI function to hand the bootstrap zip bytes from a
  bundled `.so` to Kotlin. (ASL currently exec's the native binary directly instead.)

### NDK `externalNativeBuild`, `extractNativeLibs`, `useLegacyPackaging`
- **What:** Gradle settings that (respectively) compile the C code, force native libs to be **physically
  extracted** to `nativeLibraryDir` at install, and use the older packaging that makes that extraction
  reliable.
- **Why:** without extraction, the `.so` stays compressed inside the APK and **cannot be executed** —
  breaking the whole mechanism. ASL sets all three.

---

Next: [10-full-build-run-gap-and-roadmap.md](10-full-build-run-gap-and-roadmap.md) — what's still missing
to actually build and run a real Android project, and how to make it reliable.
