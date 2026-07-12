# Indexing & language intelligence

"Indexing" is the bar that fills when Android Studio opens a project. It is a **separate concern from
building** — building produces an APK; indexing produces the *fast symbol lookups* that power
completion, go-to-definition, find-usages, and diagnostics. ASL's editor engine is currently heuristic
(pattern-based); real intelligence requires an index over the project **and its libraries**.

---

## 1. What an index actually is

An index is a **precomputed, queryable map** from a key to the places that key appears, so the editor
never rescans the whole world on each keystroke:

| Index | Key → value | Powers |
|---|---|---|
| **Class-name index** | simple name → fully-qualified names | auto-import, go-to-class |
| **Package index** | package → members | package completion |
| **Source-symbol index** | name → declarations in *your* code | go-to-symbol, find-usages |
| **Bytecode-members index** | type → its methods/fields (from library `.class`) | member completion on libraries |
| **Resource index** | res name → definition(s) | `R.*` completion, unresolved-resource checks |

Completion for `list.` works by: parse the file (error-tolerantly) → resolve the type of `list` →
query the members index for that type → rank candidates. None of that is possible without an index.

---

## 2. The two halves (this is the key architectural decision)

CodeAssist splits the index by mutability, and you should too:

### Static indices — SDK + libraries (huge, immutable)
- `android.jar` (~26 MB), the Kotlin stdlib, every AndroidX/Compose jar — **thousands of classes** that
  never change during a session.
- Built **once**, stored as **disk-backed immutable segments**, one per artifact, **keyed by content
  hash**. Queried *in place* through a **bounded block cache** — so **heap stays flat regardless of
  index size** (the only resident state is a sparse term index). This is what makes it work on a phone.
- Because segments are content-addressed, identical jars → identical segments → **shared across projects**
  under a shared cache root. Each project doesn't re-index the same Compose jars.

### Source indices — your code (small, volatile)
- Your `.kt`/`.java`/`.xml`. Kept **in memory**, updated **incrementally on every edit** — reparse the
  changed file, update its symbols, done.

> The trap to avoid: loading every library symbol into a `HashMap` in RAM. On a real project that's
> hundreds of MB of heap and an OOM on a phone. Disk-backed segments + a block cache is the answer.

---

## 3. Reading symbols out of libraries

- **Java/Android types** → read raw **bytecode** (`.class`) for classes, methods, fields, signatures.
- **Kotlin types** → decode the **`@Metadata`** annotation in the `.class` (nullability, default params,
  properties, extension functions, coroutines). Raw bytecode alone gives you Java-flavored, wrong
  Kotlin completion. CodeAssist's Kotlin backend has two symbol sources: project `.kt` parsed to a
  declaration index, and classpath binaries decoded from Kotlin metadata.

---

## 4. How the editor uses it (completion pipeline)

CodeAssist / real IDEs use an **error-tolerant AST** + the **dummy-identifier technique**:
1. Splice a marker identifier at the caret so the half-typed buffer still parses.
2. Classify the context (member access? qualifier? bare scope?).
3. Gather candidates: local scope + imports (from the source index) + unimported classes (from the
   class-name index, for auto-import).
4. **Rank** with a shared prefix/fuzzy scorer.

Diagnostics run on **tiers** (`SYNTAX` → `SEMANTIC` → `PROJECT`), debounced and cancellable, merging
**compiler errors** and **analyzer findings** into one stream, with quick-fixes keyed by diagnostic code.

---

## 5. Why indexing depends on the project model

You cannot index a file correctly without knowing its **classpath** (which libraries are visible) and
**source roots** (including generated code). That comes from **sync** (step 1). So the order is always:

```
sync (project model + classpath)  →  index (sources + libraries)  →  editor is smart
                                   ↘  build (uses the same classpath)
```

The `ClasspathSnapshot`'s content hash is a **shared cache key** for *both* the build cache and the
editor's analysis cache — so a dependency change invalidates compilation and completion together. Model
it once, use it in both places.

---

## 6. Where ASL is now vs. where this points

ASL's editor engine (`feature/editor/engine/*`) is a **standalone heuristic** layer: lexers, pattern
scanners, a hand-curated `ApiCompletionCatalog`, XML DOM + Android contributor. That's a legitimate
"Lite" starting point and matches your memory note about mirroring CodeAssist's editor behavior. To move
from *heuristic* to *project-aware* intelligence you need, in order:
- [ ] a **project model** with resolved classpaths (from sync)
- [ ] **static library/SDK indices** (disk-backed, content-hash-keyed, shared cache)
- [ ] **incremental source indices** (in-memory, per-edit)
- [ ] Kotlin **metadata decoding** for Kotlin-quality library completion
- [ ] wire the completion pipeline to query the index instead of the static catalog

This is a large amount of the CodeAssist source you'd be porting anyway per your memory notes — the
index modules (`index-api`, `index-impl`) and the language backends are the natural next port after the
editor engine.
