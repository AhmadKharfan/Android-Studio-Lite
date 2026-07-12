# Reference projects: how each one builds & runs

Two working on-device IDEs, two opposite answers. This is the teardown, with concrete files to read.

---

## A. android-code-studio (AndroidIDE fork) — **run real Gradle on the device**

### The idea
Don't reimplement anything. Ship a **real JDK** and run the **actual Gradle + Android Gradle Plugin**
on the phone, driven through the **Gradle Tooling API**, in a **separate process**. The build output is
byte-for-byte what a desktop Android Studio build produces, because it *is* a Gradle build.

### How it's wired
- **`tooling/impl/ToolingApiServerImpl.kt`** — the core. It opens a `GradleConnector` /
  `ProjectConnection` (`GradleConnector.newConnector().forProjectDirectory(...)`), can
  `useInstallation(file)` or `useGradleVersion(...)`, runs tasks with `builder.forTasks(...)`, and
  supports cancellation via `GradleConnector.newCancellationTokenSource()`.
- **`tooling/impl/Main.kt`** — the Tooling API server runs in its **own OS process**; the IDE talks to
  it over a socket/IPC (`tooling/api` — `IToolingApiServer`, `IToolingApiClient`,
  `ToolingApiLauncher`). Gradle needs a big heap and a real JVM, so it's isolated from the UI.
- **`tooling/builder-model-impl/`** — a large re-implementation of **AGP's builder model** classes
  (`DefaultVariant`, `DefaultAndroidArtifact`, `DefaultLibrary`, `com.android.build.gradle.options.*`).
  This is how it reads the *sync* model (modules, variants, dependencies) back from Gradle.
- **`tooling/plugin/AndroidIDEGradlePlugin.kt` + `AndroidIDEInitScriptPlugin.kt` + `LogSenderPlugin.kt`**
  — an **init-script plugin** injected into the user's Gradle build to expose extra model info and stream
  logs back to the IDE.
- **Termux** (`termux/` module) — provides the Unix environment: a **bundled JDK 17**, `aapt2`, the SDK,
  a real terminal. The whole Android SDK toolchain runs as native processes.

### Consequences
- ✅ **100% fidelity** — any Gradle plugin, custom task, KSP, product flavor, convention plugin "just
  works" because real Gradle executes it.
- ✅ Little to maintain semantically — AGP upgrades come "for free."
- ❌ **Heavy** — a Gradle daemon + JDK on a phone: hundreds of MB of RAM, slow cold builds, big install.
- ❌ First build downloads a real Gradle distribution + dependencies.
- ❌ You inherit all of Gradle's complexity and failure modes on a device with no easy debugging.

### Read these first
`tooling/impl/ToolingApiServerImpl.kt`, `tooling/impl/Main.kt`, `tooling/api/util/ToolingApiLauncher.kt`,
`tooling/builder-model-impl/**`, `docs/en/BUILD.md`.

---

## B. CodeAssist — **reimplement the build, no Gradle**

### The idea
A full Gradle runtime is too heavy for a phone. So CodeAssist **models projects itself**, **mimics
Gradle's incremental task engine without the daemon**, and **drives the Android toolchain (aapt2, D8/R8,
apksigner) directly**. Pure-Java tools run in-process; only `aapt2` is a subprocess.

### How it's wired (module → responsibility)
- **`project-model-api` / `project-model-impl`** — the abstract project model: `Workspace → Project →
  Module → SourceSet → ContentRoot`, `Facet` (AndroidFacet/JavaFacet), `OrderEntry` deps with scopes,
  `Module.classpath(scope, variant)` → content-hashed `ClasspathSnapshot`. Persisted as **`module.toml`**
  (declarative, not an executable script), atomic write-and-rename.
- **`build-api`** (`BuildSystem`, `Build.kt`, `Plugins.kt`, `SourceGenerator.kt`, `BuildDiagnostics.kt`)
  — the SPI: `sync`, `supports`, `createBuildGraph`, `tasks`.
- **`build-engine`** — the **generic incremental task engine**: `TaskEngine.kt`, `BuildTasks.kt`,
  `Fingerprints.kt` (up-to-date checks), `CompilerOutputParser.kt` (text→structured diagnostics),
  `DexRun.kt`, `KotlinBuild.kt`, `JavaExecTask.kt`, plus run **sandbox** (`SandboxGuard.kt`,
  `ExitGuard.kt`, `Guards.kt`).
- **`jvm-build`** (`JavaBuildSystem.kt`, `JavaPlugin.kt`) — the native **Java/Kotlin** pipeline
  (`compileJava → jar`, run graph, Kotlin wiring).
- **`android-support`** — the native **Android** pipeline: `aapt2`/`D8`/`R8`/`apksigner` ports,
  `RunDexer` (dex-and-run), the AGP-faithful DAG in `docs/build-system.md`.
- **`deps-api` / `deps-impl`** — Maven **dependency resolution** + cache + newest-wins.
- **`index-api` / `index-impl`** — the **disk-backed segment indices** (doc 03).
- **`lang-kotlin` / `lang-jdt` / `lang-xml` / `analysis-*`** — language backends (K2 for Kotlin,
  ecj/JDT for Java) + the diagnostics pipeline.
- **Gradle *compat* importer** — statically **reads** `settings.gradle(.kts)`, `build.gradle(.kts)`,
  `gradle.properties`, version catalogs, extracts the declarative shape, and maps it to the project
  model. **It never executes Gradle.** Once synced, a Gradle-imported project is identical to a native
  one. (Turing-complete scripts → tolerant parse + diagnostics + explicit overrides.)

### Consequences
- ✅ **Lightweight** — no daemon, flat-heap indexing, everything incremental and cached (incl. a shared
  cross-project dex + index cache). Fast, small, phone-native.
- ✅ Full control — structured diagnostics, in-process compile, precise incrementality.
- ❌ **You must re-create every AGP behavior** you want (R placement, variant matching, resource merge
  semantics, desugaring, R8 keep-rule ordering...). `docs/build-system.md` is a catalog of exactly how
  much detail that is.
- ❌ A build script that does something weird declaratively-invisible won't be picked up by static
  extraction — you record a diagnostic and offer an override.

### Read these first
`docs/architecture.md`, `docs/build-system.md`, `docs/language-support.md`, `docs/build-process-isolation.md`,
then `build-engine/src/main/kotlin/dev/ide/build/engine/TaskEngine.kt` and `jvm-build/.../JavaBuildSystem.kt`.

---

## C. Side by side

| Dimension | android-code-studio (Gradle) | CodeAssist (native) |
|---|---|---|
| Build correctness | 100% (real AGP) | High, but you maintain it |
| RAM / footprint | Heavy (daemon + JDK) | Light (flat heap, no daemon) |
| Cold build speed | Slow | Fast |
| Arbitrary Gradle plugins | ✅ works | ❌ not executed |
| Custom `build.gradle` logic | ✅ works | ⚠️ declarative subset + overrides |
| Maintenance vs AGP upgrades | Low (free) | High (re-track) |
| On-device toolchain | Termux JDK + native tools | in-process ecj/D8/R8/apksigner + native aapt2 |
| Sync model source | Gradle Tooling API | static parse of scripts |
| Editor/index | LSP-style (java/kotlin/xml servers) | own model + disk-backed segment index |
| Fits "Lite" branding | ✗ | ✓ |
| Process isolation | separate Tooling API process | optional `:build` process (design'd, phased) |

## D. Which idea is universal?

Both are the **same task-DAG idea** at heart. The difference is *who runs the tasks*: Gradle's daemon,
or CodeAssist's own engine. android-code-studio buys fidelity with weight; CodeAssist buys lightness with
maintenance. For a product literally named **Lite**, and given you're already porting CodeAssist's editor
engine, CodeAssist is the architecture to follow — see doc 05.
