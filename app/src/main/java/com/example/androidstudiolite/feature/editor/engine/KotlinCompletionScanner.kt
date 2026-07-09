package com.example.androidstudiolite.feature.editor.engine
object KotlinCompletionScanner {
    private const val STATE_CODE = 0
    private const val STATE_LINE_COMMENT = 1
    private const val STATE_BLOCK_COMMENT = 2
    private const val STATE_STRING = 3
    private const val STATE_RAW_STRING = 4
    private const val STATE_CHAR = 5
    private const val STATE_TEMPLATE = 6
    fun scan(text: String, caret: Int): KotlinCaretContext {
        if (caret < 0 || caret > text.length) return suppressed(text, caret)
        val lexical = scanLexical(text, caret)
        if (lexical.suppressed) return suppressed(text, caret)
        val prefixStart = extractPrefixStart(text, caret, lexical)
        val prefix = text.substring(prefixStart, caret)
        val memberAccess = prefixStart > 0 && text[prefixStart - 1] == '.'
        val qualifier = if (memberAccess) extractQualifier(text, prefixStart) else null
        val importContext = !lexical.inTemplateCode && isImportLine(text, caret)
        val composeContext = isComposeContext(text)
        val callSite = buildCallSiteContext(text, caret)
        val positionKind = when {
            importContext -> CompletionPositionKind.Import
            memberAccess && qualifier != null -> CompletionPositionKind.MemberAccess
            isTypePosition(text, prefixStart) -> CompletionPositionKind.TypeReference
            callSite != null -> CompletionPositionKind.CallArgument
            else -> CompletionPositionKind.NameReference
        }
        return KotlinCaretContext(
            suppressed = false,
            positionKind = positionKind,
            prefix = prefix,
            prefixStart = prefixStart,
            memberAccess = memberAccess,
            qualifier = qualifier,
            importContext = importContext,
            composeContext = composeContext,
            inTemplateExpression = lexical.inTemplateCode,
            callSite = callSite,
        )
    }
    fun shouldAutoPopup(text: String, caret: Int, typedChar: Char): Boolean {
        val ctx = scan(text, caret)
        if (ctx.suppressed) return false
        if (typedChar == '{' || typedChar == '}') return false
        if (ctx.prefix.isEmpty() &&
            KotlinLexUtil.isEmptyFunctionBodyLine(text, caret, ctx.prefixStart) &&
            ctx.positionKind == CompletionPositionKind.NameReference
        ) {
            return false
        }
        return when {
            typedChar == '.' -> true
            typedChar == ':' -> scan(text, caret).positionKind == CompletionPositionKind.TypeReference
            typedChar == '(' || typedChar == ',' -> ctx.callSite != null
            isIdentifierChar(typedChar) -> ctx.positionKind != CompletionPositionKind.None
            else -> false
        }
    }
    private data class LexicalState(
        val suppressed: Boolean,
        val inTemplateCode: Boolean,
    )
    private fun scanLexical(text: String, caret: Int): LexicalState {
        var state = STATE_CODE
        var templateDepth = 0
        var i = 0
        var caretInTemplateIdent = false
        var caretInTemplateExpr = false
        while (i < caret) {
            when (state) {
                STATE_CODE -> when {
                    text[i] == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                        state = STATE_LINE_COMMENT
                        i += 2
                    }
                    text[i] == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                        state = STATE_BLOCK_COMMENT
                        i += 2
                    }
                    text.startsWith("\"\"\"", i) -> {
                        state = STATE_RAW_STRING
                        i += 3
                    }
                    text[i] == '"' -> {
                        state = STATE_STRING
                        i++
                    }
                    text[i] == '\'' -> {
                        state = STATE_CHAR
                        i++
                    }
                    else -> i++
                }
                STATE_LINE_COMMENT -> {
                    if (text[i] == '\n') state = STATE_CODE
                    i++
                }
                STATE_BLOCK_COMMENT -> {
                    if (text[i] == '*' && i + 1 < text.length && text[i + 1] == '/') {
                        state = STATE_CODE
                        i += 2
                    } else {
                        i++
                    }
                }
                STATE_RAW_STRING -> {
                    if (text.startsWith("\"\"\"", i)) {
                        state = STATE_CODE
                        i += 3
                    } else {
                        i++
                    }
                }
                STATE_CHAR -> {
                    if (text[i] == '\\') i += 2 else if (text[i] == '\'') {
                        state = STATE_CODE
                        i++
                    } else {
                        i++
                    }
                }
                STATE_STRING -> {
                    when {
                        text[i] == '\\' -> i += 2
                        text.startsWith("\${", i) -> {
                            state = STATE_TEMPLATE
                            templateDepth = 1
                            i += 2
                        }
                        text[i] == '$' && i + 1 < text.length && isIdentStart(text[i + 1]) -> {
                            val identStart = i + 1
                            i = identStart
                            while (i < text.length && isIdentPart(text[i])) i++
                            if (caret in identStart..i) caretInTemplateIdent = true
                        }
                        text[i] == '"' -> {
                            state = STATE_CODE
                            i++
                        }
                        else -> i++
                    }
                }
                STATE_TEMPLATE -> {
                    when {
                        text[i] == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                            state = STATE_LINE_COMMENT
                            i += 2
                        }
                        text[i] == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                            state = STATE_BLOCK_COMMENT
                            i += 2
                        }
                        text.startsWith("\"\"\"", i) -> {
                            state = STATE_RAW_STRING
                            i += 3
                        }
                        text[i] == '"' -> {
                            state = STATE_STRING
                            i++
                        }
                        text[i] == '\'' -> {
                            state = STATE_CHAR
                            i++
                        }
                        text[i] == '{' -> {
                            templateDepth++
                            i++
                        }
                        text[i] == '}' -> {
                            templateDepth--
                            i++
                            if (templateDepth <= 0) {
                                state = STATE_STRING
                                templateDepth = 0
                            }
                        }
                        else -> i++
                    }
                }
            }
        }
        if (i == caret) {
            caretInTemplateExpr = state == STATE_TEMPLATE
        }
        val inLiteralString = state == STATE_STRING || state == STATE_RAW_STRING
        val inComment = state == STATE_LINE_COMMENT || state == STATE_BLOCK_COMMENT
        val inChar = state == STATE_CHAR
        val inTemplateCode = caretInTemplateIdent || caretInTemplateExpr
        val suppressed = (inLiteralString && !inTemplateCode) || inComment || inChar
        return LexicalState(suppressed = suppressed, inTemplateCode = inTemplateCode)
    }
    private fun extractPrefixStart(text: String, caret: Int, lexical: LexicalState): Int {
        var start = caret
        while (start > 0 && isIdentifierChar(text[start - 1])) start--
        if (lexical.inTemplateCode && start > 0 && text[start - 1] == '$') {
            while (start > 1 && isIdentifierChar(text[start - 2])) start--
        }
        return start
    }
    private fun suppressed(text: String, caret: Int): KotlinCaretContext {
        val start = caret.coerceIn(0, text.length)
        return KotlinCaretContext(
            suppressed = true,
            positionKind = CompletionPositionKind.None,
            prefix = "",
            prefixStart = start,
            memberAccess = false,
            qualifier = null,
            importContext = false,
            composeContext = isComposeContext(text),
            inTemplateExpression = false,
        )
    }
    private fun isImportLine(text: String, caret: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', caret - 1) + 1
        val line = text.substring(lineStart, caret).trimStart()
        return line.startsWith("import ") && !line.contains(';')
    }
    private fun isTypePosition(text: String, prefixStart: Int): Boolean {
        var i = prefixStart - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0) return false
        if (text[i] == ':' || text[i] == '<') return true
        val before = text.substring(0, prefixStart).trimEnd()
        return before.endsWith(" is") || before.endsWith(" as") || before.endsWith(" as?")
    }
    private fun isComposeContext(text: String): Boolean =
        text.contains("@Composable") ||
            text.contains("androidx.compose") ||
            text.contains("setContent {")
    private fun buildCallSiteContext(text: String, caret: Int): CallSiteContext? {
        val site = CallSiteScanner.enclosingCall(text, caret) ?: return null
        return CallSiteContext(
            calleeName = site.calleeName,
            activeParameterIndex = site.activeParameterIndex,
            suppliedNamedArgs = site.suppliedNamedArgs,
            editingNamedArgLabel = site.editingNamedArgLabel,
            activeNamedArg = site.activeNamedArg,
            expectedType = CallSignatureCatalog.expectedTypeAt(
                site.calleeName,
                site.activeParameterIndex,
                site.activeNamedArg,
            ),
        )
    }
    private fun extractQualifier(text: String, prefixStart: Int): String? {
        if (prefixStart <= 0 || text[prefixStart - 1] != '.') return null
        var end = prefixStart - 1
        var start = end
        while (start > 0) {
            val c = text[start - 1]
            if (isIdentifierChar(c) || c == '.') start-- else break
        }
        return text.substring(start, end).trim('.').ifEmpty { null }
    }
    private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
enum class CompletionPositionKind {
    None, Import, MemberAccess, TypeReference, NameReference, CallArgument,
}
data class KotlinCaretContext(
    val suppressed: Boolean,
    val positionKind: CompletionPositionKind,
    val prefix: String,
    val prefixStart: Int,
    val memberAccess: Boolean,
    val qualifier: String?,
    val importContext: Boolean,
    val composeContext: Boolean,
    val inTemplateExpression: Boolean,
    val callSite: CallSiteContext? = null,
)
