# From here to full build & run — the gap and the reliable path

You can now onboard, grant real permissions, and the app proves it can **execute native toolchain code
on the device**. This document explains exactly what still stands between that and "open a real Android
project, build it, and run the APK," why each missing piece exists, and how to build each one *reliably*.

Terms used here are defined from zero in [09-glossary-from-zero.md](09-glossary-from-zero.md).

---

## 1. Where we are on the whole journey

```
 [DONE]  0. App + onboarding + permissions ....................... real
 [DONE]  1. On-device native execution proven .................... real (the env probe runs)
 [GAP ]  2. Real toolchain content (JDK + Android SDK + Gradle) ... the big missing artifact
 [GAP ]  3. Project model / "sync" (read a real project) ......... not started
 [GAP ]  4. Build execution (run Gradle on device) .............. not started
 [GAP ]  5. Install + run the built APK .......................... not started
 [PART]  6. Reliability layer (versions, memory, storage, errors) . partially designed
```

Steps 2–5 are the actual "build and run." Step 6 is what makes it production-grade instead of a demo.

---

## 2. The gaps, in order, with the "why" and the reliable way to do each

### Gap 2 — Real toolchain content (the JDK / SDK / Gradle)

**What's missing:** the ~100 MB of real, Android-native (Bionic-linked) toolchain binaries. Today ASL
installs only the tiny env probe.

**Why it's not trivial:** as proven earlier with `readelf`, upstream Termux binaries hard-code
`RUNPATH=/data/data/com.termux/…`, which can't resolve in ASL's sandbox. A stock desktop JDK links glibc
and won't run on Bionic at all. So the toolchain must be **rebuilt** against Bionic *and* against ASL's
own package path.

**The reliable way (two sub-decisions):**

1. **Rebuild the packages** using Termux's `termux-packages` build system with ASL's `applicationId`
   substituted for `com.termux` (full plan in [07-termux-bootstrap-rebuild.md](07-termux-bootstrap-rebuild.md)).
   - ✅ **Finalize ASL's real `applicationId` first** — it is baked into every binary path; changing it
     later means rebuilding everything. `com.example.androidstudiolite` is a placeholder.
   - Host the output as `.tar.xz` archives + a manifest; ASL's existing installer already downloads,
     checksum-verifies, and extracts them. **No app code changes needed** for this part.

2. **Solve the exec-from-data-dir restriction (W^X).** The probe runs because it lives in the exec-safe
   `nativeLibraryDir`. A full JDK is thousands of files extracted into `filesDir`, which **cannot be
   executed** under `targetSdk ≥ 29`. Pick one:

   | Option | What it means | Trade-off |
   |---|---|---|
   | **`targetSdk = 28`** (what Termux/AndroidIDE do) | The OS grants the older exec-from-data behavior | Simple, proven; but you forgo newer-API behaviors and Play may warn about a low target |
   | **Run binaries from `nativeLibraryDir`** | Ship/link the toolchain so its executables live in the exec-safe dir | Keeps a high `targetSdk`; but awkward for a big multi-file JDK and fragile |
   | **Ship bootstrap as `lib*.so` + extract, keep exec pieces linkable** | The hybrid android-code-studio uses | Most work, most control |

   **Recommendation:** start with **`targetSdk = 28`** — it's the shortest proven path to a running JDK
   and matches both reference IDEs. Revisit only if a Play-distribution constraint forces a higher target.

### Gap 3 — Project model / "sync" (reading a real project)

**What's missing:** ASL can't yet read a `build.gradle`/`settings.gradle` project into a structured model
(modules, dependencies, variants, SDK level). The file tree today is still a fake repository.

**Why it's needed:** you can't build, index, or show a project you haven't parsed. "Sync" is the step
Android Studio runs when you open a project.

**The reliable way:** two choices, matching the two architectures in doc 04:
- **Full-build path (chosen):** let real Gradle produce the model via the **Gradle Tooling API** (what
  android-code-studio does — `tooling/**`). Most faithful; requires Gradle running on device (Gap 4).
- **Lite path:** statically parse the Gradle files into a model (CodeAssist's approach) — lighter, but
  can't handle arbitrary script logic.
Given the full-build direction, use the Tooling API model.

### Gap 4 — Build execution (running Gradle on device)

**What's missing:** actually running a build.

**Why it's needed:** this is the step that produces the APK.

**The reliable way (android-code-studio's proven design):**
- Run Gradle through the **Gradle Tooling API** in a **separate OS process** (`android:process`), so a
  Gradle crash/out-of-memory can't take down the editor UI. Port `tooling/api` + `tooling/impl`
  (`ToolingApiServerImpl`, `GradleConnector`, the init-script plugin).
- Make that process a **foreground service** with a notification (Gap 6), so Android doesn't kill a long
  build when the user switches apps.
- Stream build logs + structured problems back to a build console.

### Gap 5 — Install & run the built APK

**What's missing:** taking the APK Gradle produced and installing + launching it.

**Why it's needed:** "Run" means the user sees their app start.

**The reliable way:** use `PackageInstaller` (needs the `REQUEST_INSTALL_PACKAGES` permission already
added) to install, then an `Intent` to launch the app's main activity. Handle the install-result callback
and surface failures.

---

## 3. Reliability layer (Gap 6) — how to make it production-grade

A build that works once in a demo is not a product. These are the reliability requirements, each with its
reason:

1. **Separate build process + supervision.** Run Gradle out-of-process as a foreground service; detect its
   death (`Binder.DeathRecipient`) and surface "build process died / out of memory" while keeping the
   editor alive. *Why:* builds are memory-heavy and crash; they must not take the whole app down.
2. **Version-compatibility matrix.** The single biggest real-world failure is a mismatch between the
   project's Gradle version, its AGP version, and the installed JDK. Read `gradle-wrapper.properties` and
   `compileSdk`, install the matching pieces, and warn early on an unsupported combination. *Why:* a wrong
   combination fails cryptically deep in the build.
3. **Robust download subsystem.** Resumable, checksum-verified, retry-with-backoff, offline-aware, with a
   mirror/fallback. *Why:* the entire toolchain arrives over a phone network — treat it as failure-prone.
   (ASL already has resumable + checksum; add retry/backoff + mirrors.)
4. **Environment integrity self-heal.** On each launch, verify the JDK runs (`java -version`), the SDK
   pieces exist for the project's `compileSdk`, and re-extract/re-download anything missing instead of
   failing. *Why:* partial installs and OS cleanups happen.
5. **Memory headroom management.** Request `largeHeap`, tune the Gradle daemon's `-Xmx`, and drop to a
   single worker on low-RAM devices. *Why:* Gradle + the Kotlin compiler + R8 are heap-hungry and OOM is
   the most common on-device build failure.
6. **Storage management.** The `.gradle` cache + SDK grow to gigabytes; show usage, offer cleanup, handle
   "disk full." *Why:* phones have limited storage and users will hit the wall.
7. **Structured build output.** Parse compiler/Gradle output into a **Problems** list (jump to file:line)
   separate from the raw log. *Why:* a wall of text is unusable; this is table-stakes for an IDE.
8. **Cancellation & incrementality.** Wire the real Gradle cancellation token; keep the Gradle daemon warm
   between builds. *Why:* users cancel; warm incremental builds are the difference between 5s and 90s.
9. **Signing.** Auto-manage a debug keystore; provide UI for a release keystore. *Why:* release builds
   need real signing.

---

## 4. The prioritized roadmap (what to do next, in order)

1. **Decide `applicationId` + the exec strategy** (Gap 2.2 — likely `targetSdk = 28`). *Everything native
   downstream depends on these; deciding late means rebuilding.*
2. **Stand up the toolchain rebuild + hosting** (Gap 2.1 / doc 07) — the JDK first, then Android SDK
   build-tools, then Gradle. Wire `IDE_ENVIRONMENT_MANIFEST_URL`. *This is the long pole; start it early.*
3. **Prove `java -version` runs on device** from the extracted JDK. *The real milestone after the probe —
   it validates the whole exec + linking strategy end to end.*
4. **Port the Gradle Tooling API layer** (Gaps 3–4) from android-code-studio (`tooling/**`), running in a
   separate foreground-service process.
5. **First real build**: run `assembleDebug` on a trivial project; stream logs + problems to a build
   console.
6. **Install + run** the APK (Gap 5).
7. **Harden** (Gap 6): version matrix, memory/storage management, death detection, retries.

The first three are the make-or-break sequence: nothing else matters until a real `java` runs on the
device. Everything after is porting well-understood machinery from the reference project.

---

## 5. The honest one-paragraph summary

The onboarding and the on-device execution mechanism are **done and verified on a real emulator**. The
remaining work is dominated by **one artifact and one decision**: producing a Bionic-linked, ASL-scoped
toolchain (a Termux package rebuild — real release-engineering, not app code) and choosing how binaries
execute on modern Android (`targetSdk = 28` is the pragmatic answer). Once a real `java` runs on device,
the build/run/reliability layers are a well-mapped port of android-code-studio's `tooling/**` — larger in
volume but low in unknowns.
