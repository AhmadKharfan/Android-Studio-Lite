# Recommendation & roadmap for Android Studio Lite

## 1. Where ASL is today (honest inventory)

**What exists:**
- A polished **Compose UI** and navigation: open/create/clone project, folder picker, editor screen,
  UI designer, terminal, git, AI chat/agent, settings (incl. a **Build & Run settings screen**,
  `feature/settings/buildrun/`).
- A **heuristic editor engine** (`feature/editor/engine/`): lexers, Kotlin/Java completion scanners,
  signature help, diagnostics, smart-edit, bracket matching, an XML DOM + Android contributor, and a
  hand-curated `ApiCompletionCatalog` — modelled to mirror CodeAssist's editor behavior (per your
  memory notes).
- A clean **domain layer**: `domain/model/*` (`Project`, `ProjectTemplate`, `FileNode`, …) and
  `domain/repository/*` interfaces.

**What does NOT exist (the gap this folder is about):**
- ❌ Every repository is a **fake** (`data/fake/Fake*Repository.kt`). There is **no real
  `ProjectRepository`** reading a project from disk.
- ❌ **No project model / sync** — nothing reads `build.gradle`/`settings.gradle` into modules +
  classpaths.
- ❌ **No dependency resolution** — no Maven resolver/cache.
- ❌ **No build system** — no task engine, no `aapt2`/`D8`/`R8`/`apksigner`, no APK output.
- ❌ **No real indexing** — the editor is heuristic, not project-aware.
- ❌ **No run** — no install/launch, no dex-and-run.

So: ASL is steps 0 of the doc-00 pipeline (a UI). Steps 1–5 (sync, resolve, index, build, run) are
greenfield.

## 2. The recommendation

> **Follow the CodeAssist architecture, and port it module-by-module rather than reinventing it.**
> Native project model + generic incremental task engine + native Android pipeline
> (aapt2/D8/R8/apksigner) + disk-backed indices, with a **Gradle-compat static importer** so real
> Android projects open without a Gradle daemon.

**Why this over the android-code-studio (real-Gradle) approach:**
1. **"Lite" is the product thesis.** A Gradle daemon + bundled JDK is precisely the weight ASL exists to
   avoid. CodeAssist's flat-heap, no-daemon, everything-cached design is what "Lite" means technically.
2. **You are already porting CodeAssist's editor engine.** Your memory notes say ASL mirrors CodeAssist
   and you want to port its source for exact parity. The build engine, index, and language backends are
   the *same codebase* — continuing the port is far less work than bolting on Gradle.
3. **Shared design, shared caches, one project model** feed both the editor and the build. android-code-
   studio keeps two worlds (LSP servers + a Gradle process); CodeAssist has one model. Less to build.
4. **Debuggability.** In-process pure-Java tools you can step through beat a Gradle daemon failing
   opaquely on a phone.

**When you'd instead pick the Gradle approach:** only if "must build *any* arbitrary Gradle project with
custom plugins, byte-identical to desktop" becomes a hard product requirement. Keep android-code-studio's
`tooling/` as the reference implementation if that day comes.

**Pragmatic hybrid worth considering:** native engine as the default (fast, Lite), with an *optional*
"delegate to real Gradle" mode for projects the static importer can't handle — exactly the seam
CodeAssist's `BuildSystem` SPI already allows (native vs gradle-compat vs, hypothetically, gradle-exec).

## 3. Roadmap (phased, each phase independently useful)

### Phase 0 — Project model + sync (no building yet)
Port CodeAssist's `project-model-api`/`project-model-impl` and the **Gradle-compat importer**.
- Replace `FakeProjectRepository`/`FakeFileTreeRepository` with a real one that:
  - reads `settings.gradle(.kts)` → modules, each `build.gradle(.kts)` → plugins/`android{}`/deps,
    version catalogs, `gradle.properties`,
  - produces `Workspace → Project → Module → SourceSet → ContentRoot` + `AndroidFacet`/`JavaFacet`.
- **Outcome:** the file tree and module list are real; the UI designer & editor know the real structure.

### Phase 1 — Dependency resolution
Port `deps-api`/`deps-impl`: Maven resolver + on-device cache + newest-wins → `ClasspathSnapshot`.
- **Outcome:** real compile/runtime classpaths per module. Nothing to run yet, but everything downstream
  now has correct inputs.

### Phase 2 — Indexing + real editor intelligence
Port `index-api`/`index-impl` (disk-backed segments, shared cache) + the `lang-jdt`/`lang-kotlin`
backends; wire the completion pipeline to the index instead of `ApiCompletionCatalog`.
- **Outcome:** completion/go-to/diagnostics from the *actual* project + libraries. This is the biggest
  user-visible leap and aligns with the editor work you're already doing.

### Phase 3 — The task engine + JVM build
Port `build-api` + `build-engine` (`TaskEngine`, `Fingerprints`, `CompilerOutputParser`) + `jvm-build`.
- Wire ecj/JDT + in-process K2; get `java-lib`/`java-cli` **compiling and running** (`compileJava → jar`,
  `compileJava → dexRun → runDex` on device).
- Build a real **Build console** (Problems / Log / Steps tabs) fed by structured diagnostics — the
  `feature/settings/buildrun` screen and a new build-output panel.
- **Outcome:** you can build & run pure Kotlin/Java modules on device.

### Phase 4 — The Android pipeline
Port `android-support`: `aapt2` (ship the native binary), resource merge + `R` gen, `D8` dexing
(bucketed + content-hash cached), `packageApk`, `zipalign`, `apksigner` + debug keystore, AAR routing,
variant selection.
- **Outcome:** **a real debug APK builds on device.** Install + launch = Run works.

### Phase 5 — Release + polish
`R8` minify + ProGuard-rule gathering, `shrinkResources`, core-library desugaring (`L8`), the shared
cross-project dex cache, and (optionally) the `:build` process isolation from CodeAssist's
`build-process-isolation.md` so a build OOM can't kill the IDE.

## 4. What to reuse vs. rewrite

| Concern | Action |
|---|---|
| Project model, task engine, dex pipeline, indices, lang backends, dep resolver | **Port from CodeAssist** — don't reinvent; it's years of AGP-faithful detail. |
| aapt2 / zipalign native binaries | **Ship** (bundle per-ABI, extract on first run). |
| D8/R8/apksigner/ecj/kotlin-compiler-embeddable | **Bundle** the pure-Java jars (as CodeAssist does). |
| UI (build console, run picker, problems panel) | **Build your own** in your existing Compose design system — this is where ASL adds value. |
| Gradle-*compat* static importer | **Port + extend** for your target project shapes. |

## 5. First concrete step

Port **`project-model-api` + the Gradle-compat importer** and back a real `ProjectRepository` with it.
Everything else (index, build, run) consumes the project model, so it is the load-bearing first piece —
and it immediately makes the file tree, editor, and UI designer operate on a *real* opened project
instead of fakes.

## 6. License note

CodeAssist and android-code-studio are **GPLv3** (android-code-studio states GPLv3; check CodeAssist's
`LICENSE`). Porting their source means ASL's derived modules inherit GPLv3 obligations. Confirm this is
compatible with your intended licensing/distribution **before** copying source — this is a real
constraint, not a formality.
