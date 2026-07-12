# How an Android project builds — the full APK pipeline

This is what real Android Studio (via Gradle + the Android Gradle Plugin, "AGP") does under the hood
when you press **Run**. Everything here is what you must reproduce — whether by *executing* Gradle
(android-code-studio) or by *reimplementing* it (CodeAssist).

---

## 0. The task DAG (the single most important idea)

An Android build is a **directed acyclic graph of tasks**, each with typed **inputs** and **outputs**,
run **only when its inputs' fingerprints change**, results **cached**. This is Gradle's model, and
CodeAssist copies it faithfully:

```kotlin
interface Task {
    val name: String
    val inputs: TaskInputs    // files, dirs, scalar properties, classpath hashes
    val outputs: TaskOutputs  // files, dirs
    suspend fun execute(ctx: TaskContext): TaskResult
}
```

The engine topologically sorts the graph, runs levels with bounded parallelism, fingerprints inputs
before each task (skip on match), and streams logs + **structured diagnostics** to the build console.
**Editing one file re-runs only the affected subgraph.** If you take one idea from this whole folder,
take this one.

---

## 1. The pipeline, task by task

The debug (no-minify) DAG, faithful to AGP's shape:

```
mergeResources ─► aapt2Compile ─► aapt2Link (+ generate R) ─┐
                                                            ├─► [compileKotlin ─►] compileJava
manifest merge ─────────────────────────────────────────────┘        │
                                                                      ▼
                                              dexBuilder (per-class dex archives)
                                                                      │
                            ┌───────────────┬─────────────────────────┤
                            ▼               ▼                         ▼
                     mergeProjectDex   mergeLibDex              mergeExtDex
                            └───────────────┴─────────────┬───────────┘
                                                          ▼
                                                     packageApk
                                                          ▼
                                                    zipalign + sign
                                                          ▼
                                                    install + launch
```

Release swaps the dex chain for a single **`minifyWithR8`** pass (shrink + optimize + obfuscate + dex),
optionally preceded by resource shrinking.

---

## 2. Resources & `aapt2`

Android resources (`res/`, `AndroidManifest.xml`, `assets/`) are compiled by **`aapt2`** (Android Asset
Packaging Tool 2), a **native binary** — this is the one tool you cannot run as pure Java.

1. **Manifest merge** — the app manifest + every library/AAR manifest + build-type/flavor overrides are
   merged into one final `AndroidManifest.xml` (placeholders like `${applicationId}` substituted).
2. **`mergeResources`** — app + library + AAR resources folded into one tree. `values/` resources merge
   **by entry** keyed by `(qualifier, type, name)`, last-source-wins, so duplicates from two paths
   collapse instead of colliding.
3. **`aapt2 compile`** — each resource → a flat intermediate (`.flat`).
4. **`aapt2 link`** — links the flats + manifest against `android.jar`, produces:
   - the compiled resource table inside the base APK,
   - the **`R` class/jar** — the generated `R.java`/`R.jar` mapping every resource name to an int id,
   - (release) ProGuard keep rules for XML-referenced classes (`--proguard`).

### The `R` class, and why it's subtle

`R.string.app_name` etc. is **generated code**. Two rules matter:
- **Library modules** get a **non-final** `R` from their own resources (ids *not* inlined into their
  bytecode), so a library is compiled once, independently of the app.
- **The app** generates the **final `R`** for itself *and* every library package, and dexes it. AGP
  keeps `R` in the **project dex layer** and content-hashes it, so a resource edit re-dexes only the
  small project layer — not all ~60 AndroidX libraries.

An IDE must generate `R` before compiling code (code references `R.*`) *and* re-index it so completion
knows about `R.string.app_name`.

---

## 3. Compilation

Exactly doc 01 — but the compile classpath now includes **`android.jar`** (the platform stubs, from the
installed SDK, `compileOnly`) and the generated `R`. Order stays
`[compileKotlin →] compileJava`. Compose plugin applies here.

---

## 4. Dexing — `D8` and `R8`

The JVM runs `.class`; Android's ART runs **`.dex`**. **`D8`** converts `.class` → `.dex`; **`R8`** is
D8 + whole-program shrinking/optimization/obfuscation. Both are **pure Java** (run in-process on ART or
forked for heap headroom).

Dexing is where on-device performance is won or lost, so it's heavily bucketed and cached:

- **Three scopes** dexed separately: **project** (your code), **sub-module**, **external** (libraries).
- **Project scope** is per-class incremental — only changed classes re-dex.
- **External libraries** are per-jar **content-hash buckets** — an unchanged AndroidX/Compose jar is
  never re-dexed. CodeAssist keeps a **shared cross-project cache** so a given jar is dexed *once per
  machine*, not once per project.
- **`minSdk ≥ 21`** → native multidex (`classes.dex`, `classes2.dex`, …); below 21 → a single merge.
- **Desugaring** — D8 backports newer bytecode (default/static interface methods; `java.time` etc. with
  core-library desugaring via an **`L8`** step) so it runs on old Android versions.
- **Method count** — a single dex file caps at 65,536 methods ("the 64K limit"); multidex is the fix.

Merges (`mergeProjectDex` / `mergeLibDex` / `mergeExtDex`) combine the archives into the final dex set.
The merge is the **memory peak** of the whole build — on a phone it's often forked into a bigger-heap VM.

---

## 5. Minification (release) — `R8` + ProGuard rules

When `minifyEnabled = true`, the dex chain is replaced by one **`R8`** pass that shrinks (drops unused
code), optimizes, obfuscates (renames), and dexes — app + all libraries together. Keep rules are
gathered AGP-style, in order:
1. `aapt2` manifest/layout-derived rules (so XML-referenced Activities/custom views survive),
2. the build type's `proguardFiles` (a bundled `proguard-android-optimize.txt` + module files),
3. dependency/AAR `consumerProguardFiles`,
4. inline `proguardRules`.

The rename map is written to `outputs/mapping/<variant>/mapping.txt` (needed to de-obfuscate crashes).
**`shrinkResources`** (requires minify) drops unreachable resources in the same pass.

---

## 6. Packaging, aligning, signing

1. **`packageApk`** — zip together: compiled resources, `classes*.dex`, AAR assets, JNI `.so` libs,
   `META-INF`. Produces an **unsigned, unaligned** APK.
2. **`zipalign`** — align uncompressed entries to 4-byte boundaries (mmap performance). Native tool.
3. **`apksigner`** — sign with a keystore. **v1** (jar signature), **v2/v3** (whole-APK APK Signature
   Scheme). Debug builds use the auto-generated **debug keystore**. `apksigner` is **pure Java**.

Order matters: **align before v2/v3 sign** (v2 signs the aligned bytes).

---

## 7. Install & run

- **`adb install`** on desktop; on-device, `PackageInstaller` (or a `pm install` shell) installs the
  APK, then an `Intent` launches the launcher Activity.
- **Variants** — a build always targets one variant (`debug`/`release` × flavors). The chosen variant
  selects source sets, resources, `R`, and which library variant each dependency contributes. Model an
  **active variant** per module and let the editor analyze against that variant's classpath.

---

## 8. AAR vs JAR dependencies

- **JAR** = code only. Goes on compile + dex.
- **AAR** (Android ARchive) = code (`classes.jar`) **+ Android resources + a manifest + assets + JNI +
  `R.txt` + `consumerProguardFiles`**. You must **unzip and route each part**: code→compile/dex,
  resources→the merged app `R`, assets/JNI→the package, manifest→manifest merge.

Handling AARs correctly is a large chunk of "why is this hard."

---

## 9. Dependency resolution (step 2 of the whole flow)

Before any of this, every coordinate (`androidx.core:core-ktx:1.12.0`) must be resolved to a file:
- read `pom`/`module` metadata → transitive closure,
- download from Maven repos (google(), mavenCentral()) into a local cache,
- **conflict resolution**: two paths to different versions of the same artifact → **newest wins**
  (Gradle's rule). CodeAssist does this in `Module.classpath(...)` producing a content-hashed
  `ClasspathSnapshot`.

Getting "newest wins" wrong means two copies of a class → duplicate-class dex merge failure.

---

## 10. Structured diagnostics (making failures usable)

Raw tool output is text like `Foo.kt:12:5: error: unresolved reference: bar`. A good build system
**parses** it into structured `BuildDiagnostic(severity, message, source, file:line:col, code)` and
routes it to a **Problems** panel that jumps to `file:line` — separate from the raw **Log**. CodeAssist's
`CompilerOutputParser` understands the GNU/javac/kotlinc/aapt2 single-line form and the ecj batch-block
form. Do this from day one; it's the difference between "a build tool" and "a wall of text."

---

## 11. Checklist: everything the Android pipeline needs

- [ ] `aapt2` native binary (compile + link) → **must ship a native binary**
- [ ] manifest merger
- [ ] resource merger (by-entry) + `R` generation (final app R + non-final lib R)
- [ ] `D8` (debug) + `R8` (release) — pure-Java, bucketed + content-hash cached
- [ ] `L8` + desugar runtime (if core-library desugaring)
- [ ] `zipalign` (native) + `apksigner` (pure-Java) + debug keystore
- [ ] AAR unzip + routing
- [ ] Maven dependency resolver + cache + newest-wins conflict resolution
- [ ] variant selection
- [ ] the incremental task engine tying it all together
- [ ] a compiler-output parser + Problems panel

That is the full surface area. Doc 04 shows how each reference project covers it.
