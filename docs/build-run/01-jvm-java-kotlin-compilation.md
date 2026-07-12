# How Java & Kotlin projects compile (the JVM foundation)

Before Android enters the picture, you have to understand how ordinary JVM code becomes runnable.
Every Android build is this, plus a resource + dex + packaging stage on top.

---

## 1. The unit of compilation

- **Source set** — a directory of source files that compile together under one configuration
  (`main`, `test`, `debug`, `release`). Real Android Studio calls these source sets; Gradle models
  them the same way.
- **Classpath** — the ordered list of `.jar`/`.class` directories the compiler is allowed to resolve
  symbols against. Getting the classpath right is *the* hard part of an IDE — more than running the
  compiler.
- **Output** — a directory of `.class` files (or a `.jar`) mirroring the package structure.

### Two classpaths, always kept separate

| Classpath | Contains | Used for |
|---|---|---|
| **Compile classpath** | `api` + `implementation` + `compileOnly` deps, `android.jar` | compiling `.java`/`.kt` |
| **Runtime classpath** | `api` + `implementation` + `runtimeOnly` deps (NOT `compileOnly`, NOT `android.jar`) | packaging / running |

`compileOnly` (e.g. `android.jar`) is on the compile path but **filtered out** of the runtime/packaged
path — the device already provides it. This distinction is real and you must model it.

---

## 2. Java compilation (`javac` / ecj)

```
javac  -classpath <compile cp>  -d <out dir>  -source 17 -target 17  *.java
```

- Produces one `.class` per class (plus `$` inner classes).
- **On a phone there is no `javac` binary** and no `tools.jar`. Two options:
  - **Eclipse JDT / ecj** — a *pure-Java* compiler (the same one Eclipse uses). It runs in-process on
    ART, is error-tolerant (great for editor use), and is what CodeAssist uses as its default backend.
  - A **bundled OpenJDK** shipped in the app (what android-code-studio does via a Termux JDK).
- `-source`/`-target` (or `--release`) = the "Java language level" — model this per-module.

### Annotation processing (KAPT / APT)

`javac -processor` runs annotation processors that **generate more source** (Dagger, Room, Moshi,
data-binding). The generated sources go into a `generated/` content root and are compiled in the same
invocation. Modern projects prefer **KSP** (Kotlin Symbol Processing) which is faster and Kotlin-native.
An IDE must feed generated sources back into both the compile classpath *and* the index.

---

## 3. Kotlin compilation (`kotlinc` / K2)

Kotlin does **not** compile to `.class` the same way and cannot be ignored on a modern project:

```
kotlinc  -classpath <compile cp>  -d <out dir>  -jvm-target 17  <plugins> <-P opts>  *.kt *.java
```

Key facts that shape an IDE:

1. **Kotlin sees Java, Java sees Kotlin.** Mixed modules compile in a dance: `kotlinc` is given the
   module's `.java` files *for resolution only* (it doesn't emit them), emits Kotlin `.class` to a
   sibling dir, then `javac` compiles the `.java` with that Kotlin output added to its classpath.
   Order: **`compileKotlin` → `compileJava`.**
2. **The compiler is a library.** `kotlin-compiler-embeddable` is a jar you can call in-process — no
   `kotlinc` binary needed. CodeAssist runs the **K2 compiler in-process on ART** (`KotlinJvmCompiler`).
3. **Compiler plugins rewrite bytecode.** Kotlin plugins (`-Xplugin` / `-P`) transform the AST during
   compilation. The big one for Android is **Compose** (see below). Others: kotlinx-serialization,
   Parcelize, all-open, no-arg.
4. **Metadata.** Kotlin libraries ship a `@Metadata` blob in their `.class` files describing nullability,
   default params, properties, etc. To offer *Kotlin-quality* completion on a library you must **decode
   that metadata**, not just read raw bytecode. This is the single biggest difference between "Java
   completion on a Kotlin lib" and "real Kotlin completion."

### Jetpack Compose is a special case

The Compose compiler plugin **rewrites every `@Composable` function** — it threads a synthetic
`Composer` parameter plus `$changed`/`$default` bitmasks and wraps bodies in restart groups. **Without
the plugin the emitted bytecode does not run.** So any build that touches Compose code must:

- detect the module depends on `androidx.compose.runtime` (look for `Composable` on the classpath), and
- apply the bundled Compose compiler plugin jar to that module's `kotlinc` invocation.

CodeAssist bundles the plugin as a resource and applies it automatically — see its
`docs/kotlin-compiler-plugins-and-codegen.md`.

---

## 4. Packaging & running plain JVM code

- **`jar`** — zip the `.class` output (+ a `MANIFEST.MF`) into a `.jar`.
- **Run on desktop** — `java -cp <runtime cp> com.example.MainKt`.
- **Run on a phone** — there is **no `java` binary on ART**. You must **dex** the runtime classpath
  (turn `.class` into Android `.dex`, see doc 02) and load it with a `DexClassLoader`, then invoke
  `main` by reflection. CodeAssist's run graph on device is `compileJava → dexRun → runDex`, and it
  wraps the run in a sandbox (rewrites `System.exit`, network/file/reflection call sites) so a user
  program can't take down the IDE.

---

## 5. Incremental compilation (why an IDE feels fast)

Recompiling everything on every keystroke or every build is unusable on a phone. The trick:

- **Fingerprint inputs.** Hash each source file + the classpath. If nothing changed, skip.
- **ABI-aware invalidation.** If you edit a method *body*, only that file recompiles. If you change a
  method *signature* (its ABI), everything that depends on it recompiles. Kotlin and modern javac both
  support this; CodeAssist does per-file ABI-aware incremental Kotlin compilation.
- **Cache outputs** keyed by input fingerprint, so a clean build can restore instead of recompute.

This is not a compiler feature you turn on — it's the **task engine** wrapping the compiler. That engine
is the heart of doc 02.

---

## 6. What ASL needs from this document

To build (not just edit) Kotlin/Java you need, per module:
- [ ] a resolved **compile classpath** and **runtime classpath** (distinct)
- [ ] a **Java compiler** callable on ART → **ecj/JDT** (recommended) or a bundled JDK
- [ ] a **Kotlin compiler** callable on ART → **`kotlin-compiler-embeddable` / K2 in-process**
- [ ] **Compose plugin** wiring for Compose modules
- [ ] **annotation processing / KSP** for generated sources
- [ ] an **incremental task engine** so it's fast

The next document adds the Android-specific stages that sit on top of this.
