# Play flavor — in-process build engine (T9)

The `play` flavor builds projects **on the device, in-process on ART**, with no Gradle and no
downloaded executables (except the `aapt2` native binary shipped in the APK's `jniLibs`). It reads a
project's **declarative** shape statically (T8's `GradleProjectReader`) and runs its own incremental
task pipeline. This is a cleanroom implementation — CodeAssist / android-code-studio were read for
architecture only; no GPL code was ported.

For byte-for-byte Gradle fidelity, use the `full` flavor (out-of-process Gradle Tooling API).

## Pipeline

```
aapt2 compile → aapt2 link (+ R.java) → kotlinc (embedded) → ECJ javac → D8 (per-jar dex cache) → dex merge → package → apksig
```

| Stage | Implementation | License |
|-------|----------------|---------|
| Resource compile/link | `aapt2` native binary from `jniLibs`, exec'd from `nativeLibraryDir` | AOSP, Apache-2.0 |
| Kotlin compile | `kotlin-compiler-embeddable`, loaded in-process from a downloaded data jar into an isolated classloader | Apache-2.0 |
| Java compile | ECJ (`org.eclipse.jdt:ecj`), in-process | EPL-2.0 |
| Dexing | D8 (`com.android.tools:r8`), in-process, per-jar content-hash cache | AOSP, Apache-2.0 |
| Signing | apksig, in-process; auto debug keystore via Bouncy Castle | Apache-2.0 / BC licence |

### Modules

- **`:build:common`** — platform-neutral primitives: content fingerprinting, the incremental
  `TaskExecutor` (up-to-date checks + cancellation), and the `BuildReporter` sink.
- **`:build:engine`** — Maven resolver, tool wrappers, and the `BuildPipeline`. Android library
  (execs aapt2), but almost all logic is plain JVM and unit-tested off-device.
- **`app/src/play/.../data/build`** — `InProcessBuildSystem` (binds `BuildSystem` in the play
  `FlavorModule`), which maps `ProjectModel` → engine `BuildSpec`, resolves dependencies, and bridges
  engine reporting into the shared `BuildEvent` flow.

### Incrementality

Each task declares fingerprintable inputs/outputs. A task is skipped as `UP_TO_DATE` when the combined
content fingerprint of its inputs+outputs matches the last successful run and all outputs still exist,
so a one-file edit re-runs only the affected tail. Dependency dexing is additionally cached per jar by
content hash, so unchanged dependencies are never re-dexed.

## Dependency resolution

On-device transitive Maven resolution (`MavenResolver`):

- Reads POMs with **parent inheritance**, **property interpolation**, and
  **`<dependencyManagement>` / BOM import** (including `platform(...)`).
- **Newest-wins** conflict resolution (Gradle's default).
- Honours `<optional>` and `<exclusions>`; keeps `compile`/`runtime` scope, drops
  `test`/`provided`/`system`.
- AARs are exploded to `classes.jar` (+ inner `libs/`) for the classpath and their `res/` for aapt2.
- Artifacts are cached under the Gradle user home for **offline** reuse. Repositories: Google Maven
  then Maven Central.

## Supported Gradle subset

This engine **does not execute build scripts** — it reads their declarative shape. It targets the
shape emitted by ASL's own templates (Empty Views, Empty Compose first).

**Supported**

- `settings.gradle(.kts)` `include(...)` + `project(...).projectDir` overrides.
- Version catalogs (`libs.versions.toml`): versions, libraries, plugins, bundles.
- `android { }` `namespace`, `compileSdk`, `applicationId`, `minSdk`, `targetSdk`, build types,
  product flavors (read statically).
- Dependencies: `implementation`/`api`/`compileOnly`/`runtimeOnly` as Maven coordinates,
  catalog accessors (`libs.*`, `libs.bundles.*`), and `platform(...)` BOMs.
- Single-module and multi-module apps; a single application module assembled to a debug APK.

**Not supported (yet) — reported as diagnostics, not crashes**

- Imperative build logic (`if`/loops/`afterEvaluate`), custom tasks, convention/`buildSrc` plugins.
- Annotation processing (`kapt`/KSP), including Room/Hilt/Dagger codegen.
- Compose compiler plugin wiring beyond the template default; native (C/C++) modules.
- Project (`project(":x")`) dependencies in the compile classpath (parsed, not yet linked).
- Full AGP resource-merge semantics across many AAR dependencies (basic AAR `res/` is fed to aapt2).
- Release shrinking/obfuscation (R8) and AAB output (`bundletool`) — planned follow-ups.

Anything unrecognised surfaces as a `BuildEvent.Problem`, and — per the two-flavor design — the
`full` flavor is the escape hatch for projects outside this subset.

## Toolchain readiness

`AndroidToolchainProvider` resolves `android.jar` (downloaded platform data), the `aapt2` binary
(`nativeLibraryDir`), and the embedded kotlinc jars. Until the platform data + kotlinc are hosted and
installed (T2), a build fails fast with a clear "install the toolchain" problem rather than part-way
through.
