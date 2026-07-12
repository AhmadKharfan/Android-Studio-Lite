# Building & Running Android Projects on-device — Overview

This folder documents **what it takes to turn Android Studio Lite (ASL) from a UI shell into a tool
that can actually index, build, and run real Kotlin/Java Android projects on a phone.**

It is written by studying two reference IDEs that already do this, from opposite directions:

| Project | Strategy | One-line summary |
|---|---|---|
| **android-code-studio** (AndroidIDE fork) | **Run real Gradle on the device** | Ships a JDK + a Gradle Tooling API server in a separate process; the *actual* Android Gradle Plugin does the build. 100% faithful, heavy. |
| **CodeAssist** | **Reimplement the build, no Gradle** | Models the project itself, mimics Gradle's incremental task engine, and drives `aapt2`/`D8`/`R8`/`apksigner` directly. Lightweight, but every AGP behavior must be re-created. |

These are the **only two viable architectures** for on-device Android builds. Everything else is a
variation on one of them.

## The documents

1. **[01-jvm-java-kotlin-compilation.md](01-jvm-java-kotlin-compilation.md)** — How plain Java and
   Kotlin projects compile: `javac`, `kotlinc`/K2, the classpath, jars, incremental compilation,
   mixed Kotlin/Java, Compose. The foundation everything else sits on.
2. **[02-android-build-pipeline.md](02-android-build-pipeline.md)** — How an Android project becomes
   an installable APK, exactly as real Android Studio / AGP does it: resource merge → `aapt2` →
   `R` class → compile → `D8`/`R8` dex → merge → package → `zipalign` → sign → install.
3. **[03-indexing-and-language-intelligence.md](03-indexing-and-language-intelligence.md)** — What
   "indexing" means, why it exists, how symbol indices, resource indices, and the classpath feed
   completion / go-to / diagnostics.
4. **[04-reference-projects-analysis.md](04-reference-projects-analysis.md)** — A side-by-side teardown
   of how android-code-studio and CodeAssist each implement sync, build, run, and indexing, with the
   concrete modules/classes to read in each.
5. **[05-recommendation-for-asl.md](05-recommendation-for-asl.md)** — Where ASL is today, exactly what
   it is missing, and a phased roadmap (covers both architectures).
6. **[06-full-build-production-study.md](06-full-build-production-study.md)** — **The chosen path:** how
   to run the *real* Gradle toolchain on device (android-code-studio model) for production, byte-for-byte
   faithful builds — how it works, the app-size impact, the size solutions, and reliability requirements.
7. **[07-termux-bootstrap-rebuild.md](07-termux-bootstrap-rebuild.md)** — The concrete, evidence-verified
   blocker on the JDK specifically: upstream Termux binaries have a hardcoded, non-relocatable `RUNPATH`
   and won't run inside ASL's own app sandbox. What rebuilding a package tree scoped to ASL's own
   package id actually requires (this is what android-code-studio itself does).
8. **[08-progress-report.md](08-progress-report.md)** — What has actually been built so far (onboarding,
   real permissions, the on-device environment + native execution), file-by-file, and exactly what was
   verified on a real emulator.
9. **[09-glossary-from-zero.md](09-glossary-from-zero.md)** — Every term used across these docs explained
   from zero: what it is, why we need it, what it does (JDK, ABI, ELF, RUNPATH, dex, aapt2, W^X,
   nativeLibraryDir, Termux, and everything else).
10. **[10-full-build-run-gap-and-roadmap.md](10-full-build-run-gap-and-roadmap.md)** — Exactly what still
    stands between today and "build + run a real project," why each piece exists, the prioritized next
    steps, and the reliability requirements for a production-grade result.

## The mental model you need first

A real IDE build is **not** "call one compiler." It is a **dependency graph of small tasks**, each with
declared inputs and outputs, run only when its inputs changed, with results cached. Android Studio,
Gradle, AGP, CodeAssist, and android-code-studio are all *the same idea*:

```
                       ┌─────────────────────────────────────────┐
   source files ─────► │  1. SYNC   read project → project model  │
   build scripts       │            (modules, deps, SDK, variants)│
                       └───────────────────┬─────────────────────┘
                                           ▼
                       ┌─────────────────────────────────────────┐
                       │  2. RESOLVE  download/locate every jar,  │
   remote maven ─────► │             aar, and the Android SDK     │
                       └───────────────────┬─────────────────────┘
                                           ▼
                       ┌─────────────────────────────────────────┐
                       │  3. INDEX  scan every symbol in sources  │  ◄── powers the editor
                       │            + libraries → fast lookup      │      (completion, nav)
                       └───────────────────┬─────────────────────┘
                                           ▼
                       ┌─────────────────────────────────────────┐
                       │  4. BUILD  a DAG of tasks:               │
                       │   resources→R→compile→dex→package→sign   │  ◄── produces the .apk
                       └───────────────────┬─────────────────────┘
                                           ▼
                       ┌─────────────────────────────────────────┐
                       │  5. RUN  install the apk + launch, or    │
                       │          dex-and-run for a plain main()  │
                       └─────────────────────────────────────────┘
```

ASL today has a rich **editor UI and a heuristic editor engine** but **none of steps 1–5 for real
projects** — the repositories are all fakes. This documentation is about building steps 1–5.

## The chosen direction

The project is going to **production with real projects**, and "Lite" was only a temporary name. That
decision points to the **full build**: run the *real* Gradle + Android Gradle Plugin on the device
(the android-code-studio model) so ASL builds any project byte-for-byte like desktop Android Studio.

> **Adopt the full-build architecture: ship a real OpenJDK + Android SDK + Gradle as an on-device
> environment, and drive genuine Gradle through the Gradle Tooling API in a separate process.** The
> price is toolchain size (~150–200 MB of native toolchain per CPU architecture), which is managed with
> ABI splits + first-run download + on-demand SDK components — not by shrinking the APK.

The complete implementation study — how it works, exact app-size impact, the size solutions, and what
"fully reliable / production" requires — is **[06-full-build-production-study.md](06-full-build-production-study.md)**.
(Docs 01–04 still apply: they describe the compilation, pipeline, and indexing that Gradle performs
internally. Doc 05 keeps the native/"Lite" alternative on record for comparison.)
