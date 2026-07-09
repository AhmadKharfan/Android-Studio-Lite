package com.example.androidstudiolite.feature.editor.engine
class BuiltInCompletionProvider(
    private val language: EditorLanguage,
) : CompletionProvider {
    override fun complete(context: CompletionContext): List<CompletionItem> {
        if (context.suppressed) return emptyList()
        if (language != EditorLanguage.Kotlin && language != EditorLanguage.Java) {
            return rank(documentOnly(context), context)
        }
        val items = when (context.positionKind) {
            CompletionPositionKind.Import -> completeImport(context)
            CompletionPositionKind.MemberAccess -> completeMemberAccess(context)
            CompletionPositionKind.TypeReference -> completeTypeReference(context)
            CompletionPositionKind.CallArgument -> completeCallArgumentExpression(context)
            CompletionPositionKind.NameReference -> completeGeneral(context)
            CompletionPositionKind.None -> emptyList()
        }
        return rank(items, context)
    }
    private fun completeCallArgumentExpression(context: CompletionContext): List<CompletionItem> {
        val out = ArrayList<CompletionItem>()
        val site = context.callSite
        if (site != null) {
            out.addAll(
                CallSignatureCatalog.namedArgItems(
                    site.calleeName,
                    context.prefix,
                    site.suppliedNamedArgs,
                    site.editingNamedArgLabel,
                ),
            )
            out.addAll(CallSignatureCatalog.expectedTypeExtras(site.expectedType))
        }
        for (id in documentIdentifiers(context.text, context.prefixStart, context.caret)) {
            out.add(CompletionItem(id, id, CompletionKind.Variable))
        }
        return out
    }
    private fun completeImport(context: CompletionContext): List<CompletionItem> {
        val out = ArrayList<CompletionItem>()
        val qualifier = context.qualifier
        if (qualifier != null) {
            out.addAll(ApiCompletionCatalog.packagesUnder(qualifier))
            out.addAll(ApiCompletionCatalog.membersOf(qualifier))
        } else {
            out.addAll(ApiCompletionCatalog.importRoots())
            out.addAll(ApiCompletionCatalog.packagePathsMatching(context.prefix))
        }
        return out
    }
    private fun completeMemberAccess(context: CompletionContext): List<CompletionItem> {
        val qualifier = context.qualifier ?: return emptyList()
        val out = ArrayList<CompletionItem>()
        out.addAll(ApiCompletionCatalog.packagesUnder(qualifier))
        out.addAll(ApiCompletionCatalog.membersOf(qualifier))
        out.addAll(ApiCompletionCatalog.membersOf(qualifier.substringAfterLast('.')))
        return out
    }
    private fun completeGeneral(context: CompletionContext): List<CompletionItem> {
        val out = ArrayList<CompletionItem>()
        val inBody = KotlinLexUtil.isInFunctionBody(context.text, context.caret)
        val allowNoise = context.prefix.isNotEmpty() && !KotlinLexUtil.isEmptyFunctionBodyLine(
            context.text, context.caret, context.prefixStart,
        )
        if (allowNoise || !inBody) {
            for (kw in keywordsFor(language)) {
                if (context.prefix.isEmpty() || kw.startsWith(context.prefix, ignoreCase = true)) {
                    out.add(CompletionItem(kw, kw, CompletionKind.Keyword))
                }
            }
            for (snippet in snippetsFor(language)) {
                if (context.prefix.isEmpty() || snippet.label.startsWith(context.prefix, ignoreCase = true)) {
                    out.add(snippet)
                }
            }
        }
        if (context.prefix.isNotEmpty()) {
            out.addAll(ApiCompletionCatalog.topLevelSymbols(context.composeContext))
        }
        for (id in documentIdentifiers(context.text, context.prefixStart, context.caret)) {
            out.add(CompletionItem(id, id, CompletionKind.Variable))
        }
        return out
    }
    private fun completeTypeReference(context: CompletionContext): List<CompletionItem> {
        val out = ArrayList<CompletionItem>()
        out.addAll(ApiCompletionCatalog.kotlinTypeNames(context.prefix))
        if (context.prefix.isNotEmpty()) {
            out.addAll(
                ApiCompletionCatalog.topLevelSymbols(composeBias = false)
                    .filter { it.kind == CompletionKind.Class },
            )
        }
        return out
    }
    private fun documentOnly(context: CompletionContext): List<CompletionItem> =
        documentIdentifiers(context.text, context.prefixStart, context.caret).map {
            CompletionItem(it, it, CompletionKind.Variable)
        }
    private fun rank(items: List<CompletionItem>, context: CompletionContext): List<CompletionItem> {
        val prefix = context.prefix
        val memberAccess = context.memberAccess
        val importContext = context.importContext
        val positionKind = context.positionKind
        val seen = HashSet<String>()
        return items.asSequence()
            .filter { prefix.isEmpty() || it.label.startsWith(prefix, ignoreCase = true) ||
                it.label.takeWhile { c -> c.isLetterOrDigit() || c == '_' }
                    .startsWith(prefix, ignoreCase = true) }
            .filter { it.label != prefix }
            .filter { seen.add(it.label) }
            .sortedWith(
                compareByDescending<CompletionItem> { score(it, prefix, memberAccess, importContext, positionKind) }
                    .thenBy { it.label.length }
                    .thenBy { it.label.lowercase() },
            )
            .take(MAX_ITEMS)
            .toList()
    }
    private fun score(
        item: CompletionItem,
        prefix: String,
        memberAccess: Boolean,
        importContext: Boolean,
        positionKind: CompletionPositionKind,
    ): Int {
        var s = 0
        if (prefix.isNotEmpty()) {
            if (item.label.startsWith(prefix)) s += 100
            else if (item.label.startsWith(prefix, ignoreCase = true)) s += 80
        }
        when {
            importContext || memberAccess || positionKind == CompletionPositionKind.Import ||
                positionKind == CompletionPositionKind.MemberAccess -> when (item.kind) {
                CompletionKind.Class, CompletionKind.Function, CompletionKind.Method -> s += 50
                CompletionKind.Property -> s += 40
                CompletionKind.Keyword, CompletionKind.Snippet -> s -= 200
                else -> s += 10
            }
            positionKind == CompletionPositionKind.CallArgument -> when (item.kind) {
                CompletionKind.Parameter -> s += 120
                CompletionKind.Keyword -> s += if (item.label in EXPRESSION_KEYWORDS) 40 else -300
                CompletionKind.Snippet -> s -= 300
                else -> s += 5
            }
            positionKind == CompletionPositionKind.TypeReference -> when (item.kind) {
                CompletionKind.Class -> s += 60
                CompletionKind.Keyword, CompletionKind.Snippet, CompletionKind.Function -> s -= 200
                else -> s += 5
            }
            else -> when (item.kind) {
                CompletionKind.Function, CompletionKind.Method -> s += 30
                CompletionKind.Class -> s += 25
                CompletionKind.Snippet -> s += 20
                CompletionKind.Keyword -> s += 15
                CompletionKind.Variable -> s += 5
                else -> s += 10
            }
        }
        return s
    }
    private fun documentIdentifiers(text: String, excludeStart: Int, excludeEnd: Int): List<String> {
        val result = LinkedHashSet<String>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c.isLetter() || c == '_') {
                val start = i
                i++
                while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                if (start == excludeStart && i == excludeEnd) continue
                val word = text.substring(start, i)
                if (word.length >= MIN_IDENTIFIER_LEN) result.add(word)
            } else {
                i++
            }
        }
        return result.toList()
    }
    private companion object {
        const val MAX_ITEMS = 80
        const val MIN_IDENTIFIER_LEN = 2
        val EXPRESSION_KEYWORDS = setOf(
            "true", "false", "null", "this", "super", "if", "when", "try", "throw",
        )
        fun keywordsFor(language: EditorLanguage): Set<String> = when (language) {
            EditorLanguage.Kotlin -> KOTLIN_COMPLETION_KEYWORDS
            EditorLanguage.Java -> JAVA_COMPLETION_KEYWORDS
            else -> emptySet()
        }
        fun snippetsFor(language: EditorLanguage): List<CompletionItem> = when (language) {
            EditorLanguage.Kotlin -> KOTLIN_SNIPPETS
            EditorLanguage.Java -> JAVA_SNIPPETS
            else -> emptyList()
        }
        val KOTLIN_COMPLETION_KEYWORDS = setOf(
            "val", "var", "fun", "class", "object", "interface", "if", "else", "when", "for", "while",
            "return", "import", "package", "private", "public", "internal", "protected", "override",
            "suspend", "data", "sealed", "enum", "companion", "lateinit", "const", "true", "false", "null",
            "in", "is", "as", "by", "this", "super", "try", "catch", "finally", "throw",
        )
        val JAVA_COMPLETION_KEYWORDS = setOf(
            "public", "private", "protected", "static", "final", "class", "interface", "enum", "void",
            "int", "long", "boolean", "double", "float", "return", "if", "else", "switch", "for", "while",
            "new", "import", "package", "extends", "implements", "true", "false", "null", "this", "super",
        )
        val KOTLIN_SNIPPETS = listOf(
            CompletionItem("fun", "fun $0() {\n    \n}", CompletionKind.Snippet, "function"),
            CompletionItem("main", "fun main() {\n    $0\n}", CompletionKind.Snippet, "entry point"),
            CompletionItem("if", "if ($0) {\n    \n}", CompletionKind.Snippet),
            CompletionItem("ifelse", "if ($0) {\n    \n} else {\n    \n}", CompletionKind.Snippet),
            CompletionItem("for", "for (item in $0) {\n    \n}", CompletionKind.Snippet),
            CompletionItem("while", "while ($0) {\n    \n}", CompletionKind.Snippet),
            CompletionItem("when", "when ($0) {\n    \n}", CompletionKind.Snippet),
            CompletionItem("class", "class $0 {\n    \n}", CompletionKind.Snippet),
            CompletionItem("data", "data class $0()", CompletionKind.Snippet),
            CompletionItem("println", "println($0)", CompletionKind.Snippet),
            CompletionItem("val", "val $0 = ", CompletionKind.Snippet),
            CompletionItem("var", "var $0 = ", CompletionKind.Snippet),
        )
        val JAVA_SNIPPETS = listOf(
            CompletionItem("main", "public static void main(String[] args) {\n    $0\n}", CompletionKind.Snippet, "entry point"),
            CompletionItem("sout", "System.out.println($0);", CompletionKind.Snippet),
            CompletionItem("if", "if ($0) {\n    \n}", CompletionKind.Snippet),
            CompletionItem("for", "for (int i = 0; i < $0; i++) {\n    \n}", CompletionKind.Snippet),
            CompletionItem("class", "public class $0 {\n    \n}", CompletionKind.Snippet),
        )
    }
}
