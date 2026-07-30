package com.ahmadkharfan.androidstudiolite.feature.editor.engine.project

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind

enum class SymbolOrigin { PROJECT, DEPENDENCY }

data class ProjectSymbol(
    val simpleName: String,
    val qualifiedName: String,
    val kind: CompletionKind,
    val container: String?,
    val origin: SymbolOrigin,
)

class ProjectSymbolIndex(val symbols: List<ProjectSymbol>) {

    val simpleNames: Set<String> = symbols.mapTo(HashSet()) { it.simpleName }

    private val topLevel: List<ProjectSymbol> =
        symbols.filter { it.kind != CompletionKind.Method }

    private val types: List<ProjectSymbol> = symbols.filter { it.kind == CompletionKind.Class }

    private val byContainer: Map<String, List<ProjectSymbol>> =
        symbols.filter { it.container != null }.groupBy { it.container!! }

    private val subPackages: Map<String, Set<String>> = buildSubPackages(symbols)

    fun isEmpty(): Boolean = symbols.isEmpty()

    fun topLevelMatching(prefix: String): List<CompletionItem> =
        topLevel.asSequence()
            .filter { prefix.isEmpty() || it.simpleName.startsWith(prefix, ignoreCase = true) }
            .map { it.toItem() }
            .toList()

    fun typesMatching(prefix: String): List<CompletionItem> =
        types.asSequence()
            .filter { prefix.isEmpty() || it.simpleName.startsWith(prefix, ignoreCase = true) }
            .map { it.toItem() }
            .toList()

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
