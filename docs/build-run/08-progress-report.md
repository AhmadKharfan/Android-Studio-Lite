# Progress report — onboarding, permissions & on-device environment

This report covers everything implemented in the onboarding + environment work: what changed, why, and
exactly what was proven to work on a real emulator. It is a factual inventory; the *concepts* behind it
are explained from zero in [09-glossary-from-zero.md](09-glossary-from-zero.md), and what remains for a
full build+run is in [10-full-build-run-gap-and-roadmap.md](10-full-build-run-gap-and-roadmap.md).

---

## 1. What the app did before vs. now

**Before:** the whole app was a UI shell over *fake* repositories. Onboarding had a "Privacy" screen, a
Permissions screen that just flipped a boolean (no real Android permission was ever requested), and a
Setup screen that animated a fake progress bar with `delay()` and then declared success. Nothing touched
the real device.

**Now:** onboarding is backed by real Android APIs end to end —
- real runtime/settings **permission requests**,
- a real **on-device environment** that lays out a toolchain directory and **executes a real native
  binary** to verify the device can run on-device toolchain code,
- persistent onboarding state,
- the Settings → IDE Configuration screen reads the **same real environment** (not a fake).

---

## 2. Change inventory (every file)

### New — native code
| File | What it does |
|---|---|
| `app/src/main/cpp/asl_env_probe.c` | A tiny real C program (the "env probe") that prints `ASL_ENV_PROBE ok abi=<abi>` and exits 0. Compiled per-ABI by the NDK. |
| `app/src/main/cpp/CMakeLists.txt` | Builds the probe as an executable but names it `libasl_env_probe.so` so Android packages it as a native library (see glossary: *nativeLibraryDir*). |

### New — core layer
| File | What it does |
|---|---|
| `core/environment/IdeEnvironmentPaths.kt` | The on-device directory layout (`usr/`, `home/`, `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME`) + device-ABI detection. Mirrors android-code-studio's `Environment.java`. |
| `core/environment/IdeEnvironment.kt` | Runs native binaries under the toolchain environment variables and captures their output; `runProbe()` is the on-device "can we execute native code?" check. |
| `core/permissions/AslPermissions.kt` | The real, API-level-aware permission model: what to request, how to check each one live from the OS (storage / install-packages / notifications). |
| `core/network/NetworkMonitor.kt` | Real connectivity via `ConnectivityManager` (added by the IDE-Config wiring). |

### New — data layer
| File | What it does |
|---|---|
| `data/onboarding/AndroidOnboardingRepository.kt` | Real onboarding repo: reads permission state *live* from the OS every refresh, persists setup/onboarding-complete flags in DataStore. |
| `data/environment/AndroidIdeEnvironmentRepository.kt` | The installer. Installs a bundled runtime component **offline** (lay out prefix → symlink the native binary → **execute it to verify** → write marker) and keeps a remote-download path (resumable, checksum-verified, `.tar.xz` extraction) for the future JDK/SDK/Gradle. |
| `data/environment/EnvironmentManifest.kt` | Parses the hosted manifest JSON (component id/version/target path + per-ABI url/sha256/size). |

### New — domain layer
| File | What it does |
|---|---|
| `domain/model/IdeEnvironmentState.kt` | The component/environment state model (`IdeEnvironmentComponentStatus`: NotInstalled/Downloading/Verifying/Extracting/Installed/Failed). |
| `domain/repository/IdeEnvironmentRepository.kt` | The repository interface (`observeState`, `refresh`, `installAll`, `cancelInstall`). |

### Modified
| File | Change |
|---|---|
| `app/build.gradle.kts` | Added NDK version + CMake `externalNativeBuild`; `extractNativeLibs`-friendly `useLegacyPackaging`; `buildConfig` + `IDE_ENVIRONMENT_MANIFEST_URL` field; deps: DataStore, OkHttp, commons-compress, xz. |
| `AndroidManifest.xml` | Real permissions (storage/install/notifications/network/foreground-service); `extractNativeLibs="true"`; `requestLegacyExternalStorage`. |
| `di/KoinModules.kt` | Wired the real `AndroidOnboardingRepository`, `AndroidIdeEnvironmentRepository`, `NetworkMonitor`; removed the deleted fakes. |
| `domain/repository/OnboardingRepository.kt` | `setPermissionGranted(...)` → `refreshPermissions()` (never cache permission state). |
| `feature/onboarding/permissions/*` | Real `ActivityResultContracts` launchers, effect-driven requests, resume-refresh. |
| `feature/onboarding/setup/*` | Real per-component list from the environment repo; honest copy. |
| `feature/onboarding/complete/CompleteScreen.kt` | Honest "on-device runtime ready" copy. |
| `feature/onboarding/common/OnboardingSteps.kt`, `navigation/*` | Removed the Privacy step; wizard is now Permissions → Setup → Done. |
| `feature/settings/ideconfig/*` | Wired to the real `IdeEnvironmentRepository` + `NetworkMonitor` (was fake). |
| `feature/settings/developer/*` | Removed the "simulate offline" toggle that depended on the deleted fake. |
| `gradle/libs.versions.toml` | Version-catalog entries for the new deps. |

### Deleted (were fakes / obsolete)
`data/fake/FakeOnboardingRepository.kt`, `data/fake/FakeIdeConfigRepository.kt`,
`domain/model/IdeComponent.kt`, `domain/repository/IdeConfigRepository.kt`,
`feature/onboarding/statistics/*` (the Privacy screen).

### New — documentation
`docs/build-run/00`–`07` (architecture, pipeline, indexing, reference-project analysis, recommendation,
full-build study, Termux-rebuild blocker) plus this report set (`08`–`10`).

---

## 3. What was proven on the emulator (x86_64, API 34)

Not "it compiles" — actually executed and inspected on `emulator-5554`:

1. **The native binary runs on-device.** Direct exec of the extracted probe:
   `ASL_ENV_PROBE ok abi=x86_64 pid=…`, exit 0.
2. **The APK packages it for every ABI** (`lib/x86_64/libasl_env_probe.so`, arm64, arm, x86).
3. **The app's own code path works.** Drove onboarding through the UI:
   - Permissions screen showed all three **Granted** from *live OS checks* (not cached booleans).
   - Setup ran, and on device the app created the real prefix
     (`files/usr/bin/asl-env-probe` → symlink into `nativeLibraryDir`), executed the probe, and wrote the
     marker `files/home/.androidstudiolite/installed-runtime.properties` (`version=1-x86_64`) — the marker
     is written **only after** the probe verification passes.
   - Setup auto-advanced to **"You're ready to build."**
4. **Fresh-install repeat** (after `pm clear`) reproduced the whole flow cleanly, no crashes.

---

## 4. The one deliberate boundary

The bundled component is a small **real** binary that proves the *mechanism* (bundle → extract to an
exec-safe location → run). It is **not** the actual OpenJDK. Shipping a real ~100 MB JDK that runs on
device is a separate infrastructure project (a Termux package rebuild) documented in
[07-termux-bootstrap-rebuild.md](07-termux-bootstrap-rebuild.md); it flows through the **same** remote-
download path already built here. See doc 10 for the full remaining gap.
