package com.example.androidstudiolite.feature.editor.engine
class EditorCompletionController(
    private val lspEnabled: (EditorLanguage) -> Boolean = { false },
) {
    private val builtInCache = HashMap<EditorLanguage, BuiltInCompletionProvider>()
    private fun providersFor(language: EditorLanguage): List<CompletionProvider> {
        val builtIn = builtInCache.getOrPut(language) {
            BuiltInCompletionProvider(language)
        }
        if (!lspEnabled(language)) return listOf(builtIn)
        return when (language) {
            EditorLanguage.Kotlin -> listOf(builtIn)
            EditorLanguage.Java -> listOf(JavaLanguageServicePlaceholder, builtIn)
            else -> listOf(builtIn)
        }
    }
    fun invalidateProviders() {
        builtInCache.clear()
    }
    fun buildContext(session: EditorSession): CompletionContext {
        val text = session.text
        val caret = session.selection.caret
        if (session.language == EditorLanguage.Kotlin) {
            val kotlin = KotlinCompletionScanner.scan(text, caret)
            return CompletionContext(
                language = session.language,
                text = text,
                caret = caret,
                prefix = kotlin.prefix,
                prefixStart = kotlin.prefixStart,
                memberAccess = kotlin.memberAccess,
                qualifier = kotlin.qualifier,
                importContext = kotlin.importContext,
                composeContext = kotlin.composeContext,
                positionKind = kotlin.positionKind,
                suppressed = kotlin.suppressed,
            inTemplateExpression = kotlin.inTemplateExpression,
            callSite = kotlin.callSite,
        )
        }
        return buildLegacyContext(session)
    }
    fun query(session: EditorSession): List<CompletionItem> {
        if (!session.selection.isCollapsed) return emptyList()
        val context = buildContext(session)
        if (context.suppressed || context.positionKind == CompletionPositionKind.None) return emptyList()
        if (context.prefix.isEmpty() &&
            context.positionKind == CompletionPositionKind.NameReference &&
            session.language == EditorLanguage.Kotlin &&
            KotlinLexUtil.isEmptyFunctionBodyLine(context.text, context.caret, context.prefixStart)
        ) {
            return emptyList()
        }
        val merged = providersFor(context.language).flatMap { it.complete(context) }
        val seen = HashSet<String>()
        return merged
            .filter { isUsefulCompletion(it, context) }
            .filter { matchesPrefix(it, context) }
            .filter { seen.add(it.label) }
            .sortedWith(rankComparator(context))
            .take(MAX_ITEMS)
    }
    private fun isUsefulCompletion(item: CompletionItem, context: CompletionContext): Boolean {
        if (item.kind == CompletionKind.Keyword || item.kind == CompletionKind.Snippet) {
            when (context.positionKind) {
                CompletionPositionKind.CallArgument -> return false
                CompletionPositionKind.NameReference -> {
                    if (context.prefix.isEmpty() &&
                        KotlinLexUtil.isInFunctionBody(context.text, context.caret)
                    ) {
                        return false
                    }
                }
                else -> Unit
            }
        }
        return true
    }
    fun shouldAutoPopup(session: EditorSession, typedChar: Char): Boolean {
        if (session.language == EditorLanguage.Kotlin) {
            return KotlinCompletionScanner.shouldAutoPopup(session.text, session.selection.caret, typedChar)
        }
        return isTriggerChar(typedChar)
    }
    fun accept(session: EditorSession, item: CompletionItem) {
        val context = buildContext(session)
        val marker = item.insertText.indexOf("\$0")
        var insert = if (marker >= 0) item.insertText.removeRange(marker, marker + 2) else item.insertText
        var replaceStart = context.prefixStart
        val replaceEnd = context.caret
        if (context.importContext) {
            val pathStart = importPathStart(context.text, context.caret)
            val typedPath = context.text.substring(pathStart, context.caret)
            when {
                context.qualifier != null && insert.startsWith("${context.qualifier}.") -> {
                    insert = insert.removePrefix("${context.qualifier}.")
                    replaceStart = context.prefixStart
                }
                insert.contains('.') && typedPath.isNotEmpty() && !insert.startsWith(typedPath, ignoreCase = true) -> {
                    replaceStart = pathStart
                }
                insert.contains('.') && typedPath.isNotEmpty() && insert.startsWith(typedPath, ignoreCase = true) -> {
                    val tail = insert.substring(typedPath.length)
                    when {
                        tail.isEmpty() -> replaceStart = pathStart
                        tail.startsWith('.') -> {
                            insert = tail.removePrefix(".")
                            replaceStart = context.caret
                        }
                        else -> replaceStart = pathStart
                    }
                }
                else -> replaceStart = context.prefixStart
            }
        } else if (context.memberAccess && context.qualifier != null) {
            if (insert.startsWith("${context.qualifier}.")) {
                insert = insert.removePrefix("${context.qualifier}.")
            }
            replaceStart = context.prefixStart
        }
        val caretTarget = replaceStart + if (marker >= 0) marker else insert.length
        session.replaceRange(replaceStart, replaceEnd, insert, caret = caretTarget)
    }
    fun isTriggerChar(ch: Char): Boolean = isIdentifierChar(ch) || ch == '.'
    private fun buildLegacyContext(session: EditorSession): CompletionContext {
        val text = session.text
        val caret = session.selection.caret
        var start = caret
        while (start > 0 && isIdentifierChar(text[start - 1])) start--
        val prefix = text.substring(start, caret)
        val memberAccess = start > 0 && text[start - 1] == '.'
        val qualifier = if (memberAccess) extractQualifier(text, start) else null
        val importContext = isImportContext(text, caret)
        return CompletionContext(
            language = session.language,
            text = text,
            caret = caret,
            prefix = prefix,
            prefixStart = start,
            memberAccess = memberAccess,
            qualifier = qualifier,
            importContext = importContext,
            composeContext = session.language == EditorLanguage.Kotlin,
            positionKind = when {
                importContext -> CompletionPositionKind.Import
                memberAccess -> CompletionPositionKind.MemberAccess
                else -> CompletionPositionKind.NameReference
            },
        )
    }
    private fun rankComparator(context: CompletionContext): Comparator<CompletionItem> {
        return compareBy<CompletionItem> { matchTier(it.label, context.prefix, it.kind) }
            .thenByDescending { kindScore(it, context) }
            .thenBy { it.label.length }
            .thenBy { it.label.lowercase() }
    }
    private fun matchTier(label: String, prefix: String, kind: CompletionKind = CompletionKind.Variable): Int {
        if (prefix.isEmpty()) return 0
        val paramName = if (kind == CompletionKind.Parameter) label.removeSuffix("=").trim() else label
        val name = paramName.takeWhile { isIdentifierChar(it) }
        return when {
            paramName == prefix || name == prefix -> 0
            paramName.equals(prefix, ignoreCase = true) || name.equals(prefix, ignoreCase = true) -> 1
            paramName.startsWith(prefix) -> 2
            paramName.startsWith(prefix, ignoreCase = true) || name.startsWith(prefix, ignoreCase = true) -> 3
            else -> 4
        }
    }
    private fun kindScore(item: CompletionItem, context: CompletionContext): Int {
        var s = 0
        when (context.positionKind) {
            CompletionPositionKind.Import, CompletionPositionKind.MemberAccess -> when (item.kind) {
                CompletionKind.Class, CompletionKind.Method, CompletionKind.Function -> s += 50
                CompletionKind.Property -> s += 40
                CompletionKind.Keyword, CompletionKind.Snippet -> s -= 200
                CompletionKind.Variable -> s -= 50
                else -> s += 10
            }
            CompletionPositionKind.CallArgument -> when (item.kind) {
                CompletionKind.Parameter -> s += 120
                CompletionKind.Keyword -> s += if (item.label in EXPRESSION_KEYWORDS) 40 else -300
                CompletionKind.Snippet -> s -= 300
                CompletionKind.Variable -> s += 10
                else -> {
                    if (context.callSite?.expectedType != null &&
                        CallSignatureCatalog.matchesExpectedType(item, context.callSite.expectedType)
                    ) {
                        s += 80
                    } else if (context.callSite?.expectedType != null) {
                        s -= 100
                    } else {
                        s += 20
                    }
                }
            }
            CompletionPositionKind.TypeReference -> when (item.kind) {
                CompletionKind.Class -> s += 60
                CompletionKind.Keyword, CompletionKind.Snippet, CompletionKind.Function -> s -= 200
                else -> s += 5
            }
            CompletionPositionKind.NameReference -> when (item.kind) {
                CompletionKind.Function, CompletionKind.Method -> s += if (context.composeContext) 35 else 30
                CompletionKind.Class -> s += 25
                CompletionKind.Variable -> s += if (item.typeText == "import" || item.typeText == "local") 28 else 5
                CompletionKind.Snippet -> s += 20
                CompletionKind.Keyword -> s += 15
                else -> s += 10
            }
            else -> s += 0
        }
        if (context.composeContext && item.detail?.contains("@Composable") == true) s += 25
        return s
    }
    private companion object {
        const val MAX_ITEMS = 80
        val EXPRESSION_KEYWORDS = setOf(
            "true", "false", "null", "this", "super", "if", "when", "try", "throw",
        )
        fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
        fun extractQualifier(text: String, prefixStart: Int): String? {
            if (prefixStart <= 0 || text[prefixStart - 1] != '.') return null
            var end = prefixStart - 1
            var start = end
            while (start > 0) {
                val c = text[start - 1]
                if (isIdentifierChar(c) || c == '.') start-- else break
            }
            return text.substring(start, end).trim('.').ifEmpty { null }
        }
        fun isImportContext(text: String, caret: Int): Boolean {
            val lineStart = text.lastIndexOf('\n', caret - 1) + 1
            val line = text.substring(lineStart, caret).trimStart()
            return line.startsWith("import ") && !line.contains(';')
        }
        fun importPathStart(text: String, caret: Int): Int {
            val lineStart = text.lastIndexOf('\n', caret - 1) + 1
            val importIdx = text.indexOf("import ", lineStart)
            return if (importIdx >= 0 && importIdx < caret) importIdx + "import ".length else caret
        }
        fun matchesPrefix(item: CompletionItem, context: CompletionContext): Boolean {
            if (context.prefix.isEmpty()) return true
            if (item.kind == CompletionKind.Parameter) {
                val paramName = item.label.removeSuffix("=").trim()
                if (paramName.startsWith(context.prefix, ignoreCase = true)) return true
            }
            if (item.label.startsWith(context.prefix, ignoreCase = true)) return true
            if (item.insertText.startsWith(context.prefix, ignoreCase = true)) return true
            if (context.importContext || context.memberAccess) {
                val segment = item.label.substringAfterLast('.')
                if (segment.startsWith(context.prefix, ignoreCase = true)) return true
            }
            val name = item.label.takeWhile { isIdentifierChar(it) }
            if (name.startsWith(context.prefix, ignoreCase = true)) return true
            return fuzzyMatches(name, context.prefix)
        }
        fun fuzzyMatches(candidate: String, query: String): Boolean {
            if (query.isEmpty()) return true
            var ci = 0
            var qi = 0
            while (qi < query.length && ci < candidate.length) {
                if (candidate[ci].equals(query[qi], ignoreCase = true)) qi++
                ci++
            }
            return qi == query.length
        }
    }
}
