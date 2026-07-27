package com.ahmadkharfan.androidstudiolite.feature.editor.engine
class EditorCompletionController {
    private val builtInCache = HashMap<EditorLanguage, BuiltInCompletionProvider>()

    var projectIndex: com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex =
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex.EMPTY

    private val projectProvider =
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolCompletionProvider { projectIndex }

    private fun providersFor(language: EditorLanguage): List<CompletionProvider> {
        val builtIn = builtInCache.getOrPut(language) {
            BuiltInCompletionProvider(language)
        }
        return if (language == EditorLanguage.Kotlin || language == EditorLanguage.Java) {
            listOf(projectProvider, builtIn)
        } else {
            listOf(builtIn)
        }
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
        if (session.language == EditorLanguage.Xml) {
            return com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml.XmlBackend
                .complete(session.text, session.selection.caret, session.filePath).items
        }
        return queryHeuristic(session)
    }

    fun queryHeuristic(session: EditorSession): List<CompletionItem> {
        if (!session.selection.isCollapsed) return emptyList()
        val context = buildContext(session)
        if (context.suppressed || context.positionKind == CompletionPositionKind.None) return emptyList()
        if (session.language == EditorLanguage.Kotlin &&
            !context.memberAccess && !context.importContext &&
            KotlinLexUtil.isDeclarationNamePosition(context.text, context.prefixStart)
        ) {
            return emptyList()
        }
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
        if (session.language == EditorLanguage.Xml) {
            return com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml.XmlBackend
                .shouldAutoPopup(session.text, session.selection.caret, typedChar, session.filePath)
        }
        return isTriggerChar(typedChar)
    }
    fun accept(session: EditorSession, item: CompletionItem): String {
        if (session.language == EditorLanguage.Xml) {
            return acceptXmlCompletion(session, item)
        }
        val context = buildContext(session)
        val insertion = completionInsertion(item)
        val replacement = completionReplacement(context, insertion.text)
        val caretTarget = replacement.start + (insertion.marker.takeIf { it >= 0 } ?: replacement.text.length)
        session.replaceRange(replacement.start, context.caret, replacement.text, caret = caretTarget)
        return replacement.text
    }

    private fun acceptXmlCompletion(session: EditorSession, item: CompletionItem): String {
        val (start, _) = com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml.XmlBackend
            .replacementRangeAt(session.text, session.selection.caret, session.filePath)
        val insertion = completionInsertion(item)
        val caretTarget = start + (insertion.marker.takeIf { it >= 0 } ?: insertion.text.length)
        session.replaceRange(start, session.selection.caret, insertion.text, caret = caretTarget)
        return insertion.text
    }

    private fun completionInsertion(item: CompletionItem): CompletionInsertion {
        val marker = item.insertText.indexOf("\$0")
        val text = if (marker >= 0) item.insertText.removeRange(marker, marker + 2) else item.insertText
        return CompletionInsertion(text, marker)
    }

    private fun completionReplacement(context: CompletionContext, insert: String): CompletionReplacement = when {
        context.importContext -> importCompletionReplacement(context, insert)
        context.memberAccess && context.qualifier != null -> {
            val text = insert.removePrefix("${context.qualifier}.")
            CompletionReplacement(context.prefixStart, text)
        }
        else -> CompletionReplacement(context.prefixStart, insert)
    }

    private fun importCompletionReplacement(
        context: CompletionContext,
        insert: String,
    ): CompletionReplacement {
        val pathStart = importPathStart(context.text, context.caret)
        val typedPath = context.text.substring(pathStart, context.caret)
        return when {
            context.qualifier != null && insert.startsWith("${context.qualifier}.") ->
                CompletionReplacement(context.prefixStart, insert.removePrefix("${context.qualifier}."))
            insert.contains('.') && typedPath.isNotEmpty() && !insert.startsWith(typedPath, ignoreCase = true) ->
                CompletionReplacement(pathStart, insert)
            insert.contains('.') && typedPath.isNotEmpty() && insert.startsWith(typedPath, ignoreCase = true) -> {
                val tail = insert.substring(typedPath.length)
                when {
                    tail.isEmpty() -> CompletionReplacement(pathStart, insert)
                    tail.startsWith('.') -> CompletionReplacement(context.caret, tail.removePrefix("."))
                    else -> CompletionReplacement(pathStart, insert)
                }
            }
            else -> CompletionReplacement(context.prefixStart, insert)
        }
    }

    private data class CompletionInsertion(val text: String, val marker: Int)

    private data class CompletionReplacement(val start: Int, val text: String)
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
        val positionScore = when (context.positionKind) {
            CompletionPositionKind.Import,
            CompletionPositionKind.MemberAccess -> accessKindScore(item.kind)
            CompletionPositionKind.CallArgument -> callArgumentKindScore(item, context.callSite?.expectedType)
            CompletionPositionKind.TypeReference -> typeReferenceKindScore(item.kind)
            CompletionPositionKind.NameReference -> nameReferenceKindScore(item, context.composeContext)
            else -> 0
        }
        val composeBonus = if (context.composeContext && item.detail?.contains("@Composable") == true) 25 else 0
        return positionScore + composeBonus
    }

    private fun accessKindScore(kind: CompletionKind): Int = when (kind) {
        CompletionKind.Class, CompletionKind.Method, CompletionKind.Function -> 50
        CompletionKind.Property -> 40
        CompletionKind.Keyword, CompletionKind.Snippet -> -200
        CompletionKind.Variable -> -50
        else -> 10
    }

    private fun callArgumentKindScore(item: CompletionItem, expectedType: String?): Int = when (item.kind) {
        CompletionKind.Parameter -> 120
        CompletionKind.Keyword -> if (item.label in EXPRESSION_KEYWORDS) 40 else -300
        CompletionKind.Snippet -> -300
        CompletionKind.Variable -> 10
        else -> when {
            expectedType == null -> 20
            CallSignatureCatalog.matchesExpectedType(item, expectedType) -> 80
            else -> -100
        }
    }

    private fun typeReferenceKindScore(kind: CompletionKind): Int = when (kind) {
        CompletionKind.Class -> 60
        CompletionKind.Keyword, CompletionKind.Snippet, CompletionKind.Function -> -200
        else -> 5
    }

    private fun nameReferenceKindScore(item: CompletionItem, composeContext: Boolean): Int = when (item.kind) {
        CompletionKind.Function, CompletionKind.Method -> if (composeContext) 35 else 30
        CompletionKind.Class -> 25
        CompletionKind.Variable -> if (item.typeText == "import" || item.typeText == "local") 28 else 5
        CompletionKind.Snippet -> 20
        CompletionKind.Keyword -> 15
        else -> 10
    }
    private companion object {
        const val MAX_ITEMS = 80
        val EXPRESSION_KEYWORDS = setOf(
            "true", "false", "null", "this", "super", "if", "when", "try", "throw",
        )
        fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
        fun extractQualifier(text: String, prefixStart: Int): String? {
            if (prefixStart <= 0 || text[prefixStart - 1] != '.') return null
            val end = prefixStart - 1
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
