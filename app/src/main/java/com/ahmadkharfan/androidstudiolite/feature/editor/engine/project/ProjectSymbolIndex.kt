package com.ahmadkharfan.androidstudiolite.feature.editor.engine.project

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind

/** Where a [ProjectSymbol] came from — used only to tune ranking/detail text, never for filtering. */
enum class SymbolOrigin { PROJECT, DEPENDENCY }

/**
 * A single resolvable name the editor learned from the synced project: a top-level declaration in the
 * open module's sources, or a public class pulled from a resolved dependency artifact.
 *
 * [qualifiedName] is the fully-qualified name where known (`com.example.Foo`); for members it is the
 * owner-qualified form (`Foo.bar`). [container] is the enclosing package (for top-level symbols) or
 * owner type (for members), and drives `packagesUnder`/`membersOf` lookups.
 */
data class ProjectSymbol(
    val simpleName: String,
    val qualifiedName: String,
    val kind: CompletionKind,
    val container: String?,
    val origin: SymbolOrigin,
)

/**
 * The editor's view of everything the last sync made resolvable: project + dependency symbols indexed
 * for the three questions the completion/diagnostics layers ask — "what names exist" (unresolved-ref
 * suppression), "what top-level symbols/types start with this prefix" (name/type completion), and
 * "what lives under this qualifier" (import / member-access completion).
 *
 * Immutable and pure: [ProjectSymbolIndexer] builds one off the disk; the engine only reads it, so it
 * stays trivially unit-testable and safe to publish across threads.
 */
class ProjectSymbolIndex(val symbols: List<ProjectSymbol>) {

    /** Every simple name known to the project — the set diagnostics treats as "declared/resolvable". */
    val simpleNames: Set<String> = symbols.mapTo(HashSet()) { it.simpleName }

    /**
     * Name-referenceable symbols: top-level declarations from project sources plus public classes from
     * dependencies. Members (owned by a type) are only surfaced through [membersOf], never here.
     */
    private val topLevel: List<ProjectSymbol> =
        symbols.filter { it.kind != CompletionKind.Method }

    /** Type-position candidates: classes only. */
    private val types: List<ProjectSymbol> = symbols.filter { it.kind == CompletionKind.Class }

    /** container ("com.example" / "Foo") -> its direct children, for `packagesUnder` / `membersOf`. */
    private val byContainer: Map<String, List<ProjectSymbol>> =
        symbols.filter { it.container != null }.groupBy { it.container!! }

    /** All package segments that appear, so `packagesUnder` can offer sub-packages, not just leaves. */
    private val subPackages: Map<String, Set<String>> = buildSubPackages(symbols)

    fun isEmpty(): Boolean = symbols.isEmpty()

    /** Name-reference completions (top-level decls) whose simple name starts with [prefix]. */
    fun topLevelMatching(prefix: String): List<CompletionItem> =
        topLevel.asSequence()
            .filter { prefix.isEmpty() || it.simpleName.startsWith(prefix, ignoreCase = true) }
            .map { it.toItem() }
            .toList()

    /** Type-reference completions (classes) whose simple name starts with [prefix]. */
    fun typesMatching(prefix: String): List<CompletionItem> =
        types.asSequence()
            .filter { prefix.isEmpty() || it.simpleName.startsWith(prefix, ignoreCase = true) }
            .map { it.toItem() }
            .toList()

    /** Direct members of [qualifier] — a package's classes, or a type's members. */
    fun membersOf(qualifier: String): List<CompletionItem> {
        val normalized = qualifier.trimEnd('.')
        val direct = byContainer[normalized].orEmpty().map { it.toItem() }
        val packages = subPackages[normalized].orEmpty().map { pkg ->
            CompletionItem(pkg, pkg, CompletionKind.Class, typeText = "package")
        }
        return packages + direct
    }

    private fun ProjectSymbol.toItem(): CompletionItem {
        val detail = when (origin) {
            SymbolOrigin.PROJECT -> "project"
            SymbolOrigin.DEPENDENCY -> "dependency"
        }
        return CompletionItem(
            label = simpleName,
            insertText = simpleName,
            kind = kind,
            detail = detail,
            typeText = if (qualifiedName != simpleName) qualifiedName else kind.name.lowercase(),
        )
    }

    companion object {
        val EMPTY = ProjectSymbolIndex(emptyList())

        /**
         * For every package that owns a class, map each of its prefixes to the next segment, so
         * `packagesUnder("com")` yields {"example"} and `packagesUnder("com.example")` yields {"app"}.
         * Only class containers are real packages; a member's owner type never forms a package chain.
         */
        private fun buildSubPackages(symbols: List<ProjectSymbol>): Map<String, Set<String>> {
            val out = HashMap<String, MutableSet<String>>()
            val packages = symbols.asSequence()
                .filter { it.kind == CompletionKind.Class }
                .mapNotNull { it.container }
                .filter { it.isNotEmpty() }
                .toHashSet()
            for (pkg in packages) {
                val segments = pkg.split('.')
                for (i in 0 until segments.size - 1) {
                    val parent = segments.subList(0, i + 1).joinToString(".")
                    out.getOrPut(parent) { HashSet() }.add(segments[i + 1])
                }
            }
            return out
        }
    }
}
