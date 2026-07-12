package com.ahmadkharfan.androidstudiolite.feature.editor.engine
object LanguageDiagnostics {
    fun analyze(text: String, language: EditorLanguage, semantic: Boolean = true, filePath: String = ""): List<Diagnostic> = when (language) {
        EditorLanguage.Kotlin, EditorLanguage.Java -> analyzeCode(text, language, semantic)
        EditorLanguage.Xml -> com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml.XmlBackend.analyze(text, filePath)
        EditorLanguage.Plain -> emptyList()
    }


    fun kotlinHeuristic(text: String, semantic: Boolean = true): List<Diagnostic> =
        analyzeCode(text, EditorLanguage.Kotlin, semantic)

    private fun analyzeCode(text: String, language: EditorLanguage, semantic: Boolean = true): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()
        val stack = ArrayDeque<Pair<Char, Int>>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    i += 2
                    while (i < n && text[i] != '\n') i++
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    i += 2
                    while (i < n && !(text[i] == '*' && i + 1 < n && text[i + 1] == '/')) i++
                    i = if (i < n) i + 2 else n
                }
                language == EditorLanguage.Kotlin && text.startsWith("\"\"\"", i) -> {
                    val end = text.indexOf("\"\"\"", i + 3)
                    i = if (end < 0) n else end + 3
                }
                c == '"' -> i = scanString(text, i, out)
                c == '\'' -> i = scanChar(text, i, out)
                c == '(' || c == '[' || c == '{' -> { stack.addLast(c to i); i++ }
                c == ')' || c == ']' || c == '}' -> {
                    val open = stack.lastOrNull()
                    if (open == null) {
                        out.add(Diagnostic(i, i + 1, DiagnosticSeverity.Error, "unexpected '$c'", code = DiagnosticCodes.KT_SYNTAX))
                    } else if (matches(open.first, c)) {
                        stack.removeLast()
                    } else {
                        out.add(Diagnostic(i, i + 1, DiagnosticSeverity.Error, "mismatched '$c', expected '${closeOf(open.first)}'", code = DiagnosticCodes.KT_SYNTAX))
                        stack.removeLast()
                    }
                    i++
                }
                else -> i++
            }
        }
        for ((open, offset) in stack) {
            out.add(Diagnostic(offset, offset + 1, DiagnosticSeverity.Error, "missing closing '${closeOf(open)}'", code = DiagnosticCodes.KT_SYNTAX))
        }
        out.addAll(unusedImports(text))
        if (language == EditorLanguage.Kotlin) {
            out.addAll(declarationSyntaxChecks(text))
            out.addAll(danglingOperatorChecks(text))
            out.addAll(topLevelStatementChecks(text))
            out.addAll(parseChecks(text))
            out.addAll(composeChecks(text))
        }
        if (semantic && language == EditorLanguage.Kotlin) {
            out.addAll(semanticDiagnostics(text))
        }
        return out.sortedBy { it.start }
    }


    private val COMPOSABLE_ANNOTATION = Regex("""@Composable\b""")

    private fun composeChecks(text: String): List<Diagnostic> {
        if (!text.contains("@Composable")) return emptyList()
        val inCode = codeMask(text)
        val out = ArrayList<Diagnostic>()
        for (m in COMPOSABLE_ANNOTATION.findAll(text)) {
            val at = m.range.first
            if (at < inCode.size && !inCode[at]) continue
            composableNameWarning(text, m.range.last + 1)?.let { out += it }
        }
        return out
    }

    private fun composableNameWarning(text: String, from: Int): Diagnostic? {
        val funKw = nextFunKeyword(text, from) ?: return null
        var i = funKw + 3
        i = skipSpace(text, i)
        if (i < text.length && text[i] == '<') { val c = matchDelimiter(text, i, '<', '>'); if (c != null) i = skipSpace(text, c + 1) }
        val nameStart = i
        var j = i
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '.')) j++
        val token = text.substring(nameStart, j)
        val simpleName = token.substringAfterLast('.')
        if (simpleName.isEmpty() || !simpleName[0].isLetter()) return null
        if (!simpleName[0].isLowerCase()) return null
        val paren = text.indexOf('(', j).takeIf { it in j until text.length } ?: return null
        val close = matchDelimiter(text, paren, '(', ')') ?: return null
        val afterParams = text.substring(close + 1, lineEndOrBrace(text, close + 1))
        if (afterParams.trimStart().startsWith(":")) return null
        val displayEnd = nameStart + simpleName.length
        return Diagnostic(
            token.lastIndexOf('.').let { if (it >= 0) nameStart + it + 1 else nameStart }, displayEnd,
            DiagnosticSeverity.Warning,
            "Composable functions that return Unit should start with an uppercase letter",
            code = "compose.composableNaming",
        )
    }

    private fun nextFunKeyword(text: String, from: Int): Int? {
        var i = from
        while (i < text.length) {
            if (text[i] == '{' || text[i] == '}') return null
            if (text.startsWith("fun", i) &&
                (i == 0 || !(text[i - 1].isLetterOrDigit() || text[i - 1] == '_')) &&
                (i + 3 >= text.length || !(text[i + 3].isLetterOrDigit() || text[i + 3] == '_'))
            ) return i
            i++
        }
        return null
    }

    private fun lineEndOrBrace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] != '{' && text[i] != '=' && text[i] != '\n') i++
        return i
    }


    private fun declarationSyntaxChecks(text: String): List<Diagnostic> {
        val inCode = codeMask(text)
        val refs = identifiers(text, inCode)
        val out = ArrayList<Diagnostic>()
        for ((index, r) in refs.withIndex()) {
            if (r.prev == '.' || r.prev == '@') continue
            when (r.name) {
                "val", "var" -> declNameError(text, r, "Expecting a property name", allowParen = true)?.let { out += it }
                "fun" -> declNameError(text, r, "Expecting function name", allowParen = true, allowAngle = true)?.let { out += it }
                "class", "interface" -> declNameError(text, r, "Name expected", allowParen = false)?.let { out += it }
                "typealias" -> declNameError(text, r, "Expecting a name", allowParen = false)?.let { out += it }
                "if", "while", "for" -> expectsParen(text, r)?.let { out += it }
                "import", "package" -> emptyQualifiedName(text, r)?.let { out += it }
            }
            if (r.next == ':' && isNameLike(r) && index > 0) {
                missingTypeError(text, r)?.let { out += it }
            }
            if (r.next == '=' && r.name !in KOTLIN_KEYWORDS) {
                emptyAssignmentValue(text, r)?.let { out += it }
            }
        }
        return out
    }

    private fun isNameLike(r: Ref): Boolean = r.name !in KOTLIN_KEYWORDS

    private fun declNameError(text: String, r: Ref, message: String, allowParen: Boolean, allowAngle: Boolean = false): Diagnostic? {
        val i = firstMeaningfulSameLine(text, r.end)
        val bad = when {
            i < 0 -> true
            else -> {
                val c = text[i]
                when {
                    c.isLetter() || c == '_' || c == '`' -> readWordAt(text, i) in KOTLIN_KEYWORDS
                    allowParen && c == '(' -> false
                    allowAngle && c == '<' -> false
                    else -> true
                }
            }
        }
        return if (bad) Diagnostic(r.start, r.end, DiagnosticSeverity.Error, message, code = DiagnosticCodes.KT_SYNTAX) else null
    }

    private fun missingTypeError(text: String, r: Ref): Diagnostic? {
        var colon = r.end
        while (colon < text.length && (text[colon] == ' ' || text[colon] == '\t')) colon++
        if (colon >= text.length || text[colon] != ':') return null
        if (colon + 1 < text.length && text[colon + 1] == ':') return null
        val lineStart = text.lastIndexOf('\n', r.start - 1) + 1
        val lineHead = text.substring(lineStart, r.start)
        if (Regex("""\b(class|interface|object|enum)\b""").containsMatchIn(lineHead)) return null
        val i = firstMeaningful(text, colon + 1)
        val empty = i < 0 || text[i] == '=' || text[i] == ',' || text[i] == ')' || text[i] == '{'
        return if (empty) Diagnostic(colon, colon + 1, DiagnosticSeverity.Error, "Type expected", code = DiagnosticCodes.KT_SYNTAX) else null
    }

    private fun expectsParen(text: String, r: Ref): Diagnostic? {
        val i = firstMeaningfulSameLine(text, r.end)
        if (i < 0 || text[i] != '(') return Diagnostic(r.start, r.end, DiagnosticSeverity.Error, "Expecting '('", code = DiagnosticCodes.KT_SYNTAX)
        if (r.name != "for") {
            val inner = firstMeaningful(text, i + 1)
            if (inner >= 0 && text[inner] == ')') {
                return Diagnostic(inner, inner + 1, DiagnosticSeverity.Error, "Expecting an expression", code = DiagnosticCodes.KT_SYNTAX)
            }
        }
        return null
    }

    private val TWO_CHAR_BINARY = setOf("&&", "||", "==", "!=", "<=", ">=", "?:")

    private fun danglingOperatorChecks(text: String): List<Diagnostic> {
        val inCode = codeMask(text)
        val out = ArrayList<Diagnostic>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (!inCode[i]) { i++; continue }
            if (i + 1 < n && text.substring(i, i + 2) in TWO_CHAR_BINARY) {
                if (danglingBefore(text, i + 2)) out += opError(i, i + 2)
                i += 2
                continue
            }
            val c = text[i]
            if ((c == '+' && i + 1 < n && text[i + 1] == '+') || (c == '-' && i + 1 < n && text[i + 1] == '-')) {
                i += 2
                continue
            }
            if ((c == '+' || c == '-' || c == '*' || c == '/' || c == '%') && isBinaryOperator(text, i) && danglingBefore(text, i + 1)) {
                out += opError(i, i + 1)
            }
            i++
        }
        return out
    }

    private fun opError(s: Int, e: Int) =
        Diagnostic(s, e, DiagnosticSeverity.Error, "Expecting an expression", code = DiagnosticCodes.KT_SYNTAX)

    private fun isBinaryOperator(text: String, i: Int): Boolean {
        val next = if (i + 1 < text.length) text[i + 1] else ' '
        if (next == '=') return false
        when (text[i]) {
            '+' -> if (next == '+') return false
            '-' -> if (next == '-' || next == '>') return false
            '*' -> if (i > 0 && text[i - 1] == '.') return false
            '/' -> if (next == '/' || next == '*') return false
        }
        return true
    }

    private fun danglingBefore(text: String, from: Int): Boolean {
        var i = from
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == ')' || c == ']' || c == '}' || c == ',' || c == ';' -> return true
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> { while (i < text.length && text[i] != '\n') i++ }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length && !(text[i] == '*' && i + 1 < text.length && text[i + 1] == '/')) i++
                    i = if (i < text.length) i + 2 else text.length
                }
                else -> return false
            }
        }
        return true
    }

    private fun emptyAssignmentValue(text: String, r: Ref): Diagnostic? {
        var eq = r.end
        while (eq < text.length && (text[eq] == ' ' || text[eq] == '\t')) eq++
        if (eq >= text.length || text[eq] != '=') return null
        if (eq + 1 < text.length && text[eq + 1] in "=><") return null
        if (eq > 0 && text[eq - 1] in "!<>+-*/%") return null
        var i = eq + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == ',' || c == ')' || c == '}' || c == ';' ->
                    return Diagnostic(eq, eq + 1, DiagnosticSeverity.Error, "Expecting an expression", code = DiagnosticCodes.KT_SYNTAX)
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> { while (i < text.length && text[i] != '\n') i++ }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length && !(text[i] == '*' && i + 1 < text.length && text[i + 1] == '/')) i++
                    i = if (i < text.length) i + 2 else text.length
                }
                else -> return null
            }
        }
        return Diagnostic(eq, eq + 1, DiagnosticSeverity.Error, "Expecting an expression", code = DiagnosticCodes.KT_SYNTAX)
    }

    private fun emptyQualifiedName(text: String, r: Ref): Diagnostic? {
        val i = firstMeaningfulSameLine(text, r.end)
        val ok = i >= 0 && (text[i].isLetter() || text[i] == '_' || text[i] == '`')
        return if (ok) null else Diagnostic(r.start, r.end, DiagnosticSeverity.Error, "Expecting qualified name", code = DiagnosticCodes.KT_SYNTAX)
    }

    private fun firstMeaningful(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '}' || c == ';' -> return -1
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> { while (i < text.length && text[i] != '\n') i++ }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length && !(text[i] == '*' && i + 1 < text.length && text[i + 1] == '/')) i++
                    i = if (i < text.length) i + 2 else text.length
                }
                else -> return i
            }
        }
        return -1
    }

    private fun firstMeaningfulSameLine(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> return -1
                c == ' ' || c == '\t' || c == '\r' -> i++
                c == '}' || c == ';' -> return -1
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> return -1
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length && !(text[i] == '*' && i + 1 < text.length && text[i + 1] == '/')) i++
                    i = if (i < text.length) i + 2 else text.length
                }
                else -> return i
            }
        }
        return -1
    }

    private fun readWordAt(text: String, from: Int): String {
        var i = from
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return text.substring(from, i)
    }


    private val TOP_LEVEL_DECL_STARTERS = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data", "value", "abstract",
        "annotation", "typealias", "import", "package", "open", "final", "private", "public", "protected",
        "internal", "inline", "noinline", "crossinline", "suspend", "operator", "infix", "external", "const",
        "lateinit", "expect", "actual", "tailrec",
    )
    private val CONTINUATION_LEAD_WORDS = setOf(
        "get", "set", "by", "where", "else", "catch", "finally", "in", "is", "as",
        "to", "and", "or", "xor", "shl", "shr", "ushr", "step", "until", "downTo",
    )
    private val INFIX_TAIL_WORDS = setOf(
        "to", "and", "or", "xor", "shl", "shr", "ushr", "step", "until", "downTo",
        "by", "where", "in", "is", "as", "return",
    )

    private fun topLevelStatementChecks(text: String): List<Diagnostic> {
        val inCode = codeMask(text)
        val n = text.length
        val out = ArrayList<Diagnostic>()
        var depth = 0
        var prevChar = ' '
        var prevWord = ""
        var seenLeadThisLine = false
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '\n') { seenLeadThisLine = false; i++; continue }
            if (!inCode[i] || c == ' ' || c == '\t' || c == '\r') { i++; continue }
            if (!seenLeadThisLine) {
                seenLeadThisLine = true
                if (depth == 0) topLevelLeadError(text, i, prevChar, prevWord)?.let { out += it }
            }
            when (c) {
                '(', '[', '{' -> { depth++; prevChar = c; prevWord = ""; i++ }
                ')', ']', '}' -> { if (depth > 0) depth--; prevChar = c; prevWord = ""; i++ }
                else -> {
                    if (c.isLetter() || c == '_') {
                        val w = readWordAt(text, i)
                        prevWord = w; prevChar = if (w.isNotEmpty()) w.last() else c
                        i += w.length.coerceAtLeast(1)
                    } else { prevChar = c; prevWord = ""; i++ }
                }
            }
        }
        return out
    }

    private fun topLevelLeadError(text: String, i: Int, prevChar: Char, prevWord: String): Diagnostic? {
        val c = text[i]
        if (c == '@' || c == '`' || c == '#') return null
        if (c in ".,)]}=<>+-*/%&|^?:!~;") return null
        val isWord = c.isLetter() || c == '_'
        val word = if (isWord) readWordAt(text, i) else ""
        if (isWord && (word in TOP_LEVEL_DECL_STARTERS || word in CONTINUATION_LEAD_WORDS)) return null
        if (prevChar in "=,.([{+-*/%&|^<>?:!~@") return null
        if (prevWord in INFIX_TAIL_WORDS) return null
        val end = when {
            isWord -> i + word.length
            c.isDigit() -> { var j = i; while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_')) j++; j }
            else -> return null
        }
        return Diagnostic(i, end, DiagnosticSeverity.Error, "Expecting a top level declaration", code = DiagnosticCodes.KT_SYNTAX)
    }


    private class Ref(val name: String, val start: Int, val end: Int, val prev: Char, val next: Char)

    private fun semanticDiagnostics(text: String): List<Diagnostic> {
        val inCode = codeMask(text)
        val refs = identifiers(text, inCode)
        val imports = importSimpleNames(text)
        if (imports.wildcard) {
            return emptyList()
        }
        val declared = declaredNames(text, refs) + imports.names + BUILTINS + KOTLIN_KEYWORDS + KOTLIN_SOFT_KEYWORDS
        val out = ArrayList<Diagnostic>()
        for ((index, r) in refs.withIndex()) {
            if (r.name.length < 2) continue
            if (r.prev == '.' || r.next == '.') continue
            if (r.prev == '@' || r.prev == '`') continue
            if (r.next == ':' || r.next == '@') continue
            if (r.next == '=' && charAfter(text, r.end) != '=') continue
            val prevWord = if (index > 0) refs[index - 1].name else ""
            if (prevWord in DECL_KEYWORDS && adjacent(text, refs[index - 1].end, r.start)) continue
            if (r.name in declared) continue
            if (onImportOrPackageLine(text, r.start)) continue
            out += Diagnostic(
                r.start, r.end, DiagnosticSeverity.Error,
                "Unresolved reference: ${r.name}", code = DiagnosticCodes.KT_UNRESOLVED,
            )
        }
        return out
    }

    private fun codeMask(text: String): BooleanArray {
        val n = text.length
        val mask = BooleanArray(n)
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> { while (i < n && text[i] != '\n') i++ }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    i += 2
                    while (i < n && !(text[i] == '*' && i + 1 < n && text[i + 1] == '/')) i++
                    i = if (i < n) i + 2 else n
                }
                text.startsWith("\"\"\"", i) -> {
                    i += 3
                    val e = text.indexOf("\"\"\"", i)
                    i = if (e < 0) n else e + 3
                }
                c == '"' || c == '\'' -> {
                    i++
                    while (i < n && text[i] != c && text[i] != '\n') { if (text[i] == '\\') i++; i++ }
                    if (i < n && text[i] == c) i++
                }
                else -> { mask[i] = true; i++ }
            }
        }
        return mask
    }

    private fun identifiers(text: String, inCode: BooleanArray): List<Ref> {
        val out = ArrayList<Ref>()
        val n = text.length
        var i = 0
        while (i < n) {
            if (inCode[i] && (text[i].isLetter() || text[i] == '_')) {
                val start = i
                i++
                while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                out += Ref(text.substring(start, i), start, i, prevNonSpace(text, start), nextNonSpace(text, i))
            } else i++
        }
        return out
    }

    private fun prevNonSpace(text: String, from: Int): Char {
        var j = from - 1
        while (j >= 0 && (text[j] == ' ' || text[j] == '\t')) j--
        return if (j >= 0) text[j] else ' '
    }

    private fun nextNonSpace(text: String, from: Int): Char {
        var j = from
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        return if (j < text.length) text[j] else ' '
    }

    private fun charAfter(text: String, from: Int): Char = if (from < text.length) text[from] else ' '

    private fun adjacent(text: String, aEnd: Int, bStart: Int): Boolean {
        for (k in aEnd until bStart) {
            if (text[k] == '\n') return false
            if (!text[k].isWhitespace()) return false
        }
        return true
    }

    private class Imports(val names: Set<String>, val wildcard: Boolean)

    private fun importSimpleNames(text: String): Imports {
        val names = HashSet<String>()
        var wildcard = false
        for (line in text.split('\n')) {
            val t = line.trimStart()
            if (!t.startsWith("import ")) continue
            var path = t.removePrefix("import ").substringBefore(';').trim()
            val asIdx = path.indexOf(" as ")
            if (asIdx >= 0) { names += path.substring(asIdx + 4).trim(); continue }
            if (path.endsWith(".*") || path == "*") { wildcard = true; continue }
            val simple = path.substringAfterLast('.')
            if (simple.isNotEmpty()) names += simple
        }
        return Imports(names, wildcard)
    }

    private val DECL_KEYWORDS = setOf("fun", "val", "var", "class", "object", "interface", "typealias", "annotation", "enum")

    private fun declaredNames(text: String, refs: List<Ref>): Set<String> {
        val declared = HashSet<String>()
        for ((index, r) in refs.withIndex()) {
            val prevWord = if (index > 0) refs[index - 1].name else ""
            if (prevWord in DECL_KEYWORDS && adjacent(text, refs[index - 1].end, r.start)) declared += r.name
            if (r.next == ':') declared += r.name
            if (r.next == '-' && charAfter(text, r.end + 1) == '>') declared += r.name
        }
        for (m in Regex("""\b(?:val|var|for)\s*\(([^)]*)\)""").findAll(text)) {
            for (part in m.groupValues[1].split(',')) {
                val name = part.trim().substringBefore(':').trim().takeWhile { it.isLetterOrDigit() || it == '_' }
                if (name.isNotEmpty()) declared += name
            }
        }
        for (m in Regex("""\bfor\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s+in\b""").findAll(text)) declared += m.groupValues[1]
        return declared
    }

    private fun onImportOrPackageLine(text: String, offset: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val head = text.substring(lineStart, offset).trimStart()
        return head.startsWith("import ") || head.startsWith("package ") ||
            text.substring(lineStart).trimStart().let { it.startsWith("import ") || it.startsWith("package ") }
    }


    private fun parseChecks(text: String): List<Diagnostic> {
        val inCode = codeMask(text)
        val refs = identifiers(text, inCode)
        val out = ArrayList<Diagnostic>()
        valReassignment(text, refs, out)
        lateinitMisuse(text, inCode, out)
        propertyInitChecks(text, refs, inCode, out)
        redundantNullAssertions(text, inCode, out)
        unusedParameters(text, inCode, out)
        return out
    }

    private fun valReassignment(text: String, refs: List<Ref>, out: MutableList<Diagnostic>) {
        val valNames = HashSet<String>()
        val varNames = HashSet<String>()
        for ((i, r) in refs.withIndex()) {
            if (i == 0) continue
            val pw = refs[i - 1].name
            if (!adjacent(text, refs[i - 1].end, r.start)) continue
            if (pw == "val") valNames += r.name
            if (pw == "var") varNames += r.name
        }
        val immutable = valNames - varNames
        if (immutable.isEmpty()) return
        for ((i, r) in refs.withIndex()) {
            if (r.prev == '.' || r.next != '=') continue
            val eq = firstNonSpaceIndex(text, r.end)
            if (eq < 0 || text[eq] != '=') continue
            if (eq + 1 < text.length && text[eq + 1] == '=') continue
            val pw = if (i > 0) refs[i - 1].name else ""
            if ((pw == "val" || pw == "var") && adjacent(text, refs[i - 1].end, r.start)) continue
            if (r.name in immutable) {
                out += Diagnostic(r.start, r.end, DiagnosticSeverity.Error, "Val cannot be reassigned", code = DiagnosticCodes.KT_VAL_REASSIGN)
            }
        }
    }

    private fun lateinitMisuse(text: String, inCode: BooleanArray, out: MutableList<Diagnostic>) {
        var i = 0
        while (i < text.length) {
            if (inCode[i] && text.startsWith("lateinit", i) && wordBoundaryBefore(text, i) && wordBoundaryAfter(text, i + 8)) {
                val declEnd = lineEnd(text, i)
                val decl = text.substring(i, declEnd)
                lateinitMessage(decl)?.let { out += Diagnostic(i, i + 8, DiagnosticSeverity.Error, it, code = DiagnosticCodes.KT_LATEINIT) }
                i += 8
            } else i++
        }
    }

    private fun lateinitMessage(decl: String): String? {
        if (!Regex("""\bvar\b""").containsMatchIn(decl)) return "'lateinit' modifier is allowed only on mutable properties"
        if (hasPlainAssignment(decl)) return "'lateinit' modifier is not allowed on properties with an initializer"
        if (Regex("""\bby\b""").containsMatchIn(decl)) return "'lateinit' modifier is not allowed on delegated properties"
        val type = typeAfterColon(decl) ?: return "'lateinit' modifier requires the property to have an explicit type"
        if (type.trim().endsWith("?")) return "'lateinit' modifier is not allowed on properties of nullable types"
        return null
    }

    private fun propertyInitChecks(text: String, refs: List<Ref>, inCode: BooleanArray, out: MutableList<Diagnostic>) {
        for ((i, r) in refs.withIndex()) {
            if (i == 0) continue
            val pw = refs[i - 1].name
            if ((pw != "val" && pw != "var") || !adjacent(text, refs[i - 1].end, r.start)) continue
            if (hasModifierBefore(text, refs[i - 1].start, setOf("lateinit", "abstract", "const", "expect", "external"))) continue
            val declEnd = lineEnd(text, r.start)
            val rest = text.substring(r.end, declEnd)
            val hasType = r.next == ':'
            val hasInit = hasPlainAssignment(rest)
            val hasDelegate = Regex("""\bby\b""").containsMatchIn(rest)
            val hasAccessor = Regex("""\b(get|set)\s*\(""").containsMatchIn(rest)
            if (hasInit || hasDelegate || hasAccessor) continue
            when {
                !hasType -> {
                    val member = enclosingContainer(text, r.start, inCode) != Container.FUNCTION
                    val msg = if (member) "This property must either have a type annotation, be initialized or be delegated"
                    else "This variable must either have a type annotation or be initialized"
                    out += Diagnostic(r.start, r.end, DiagnosticSeverity.Error, msg, code = DiagnosticCodes.KT_NO_TYPE_NO_INITIALIZER)
                }
                else -> {
                    val c = enclosingContainer(text, r.start, inCode)
                    if (c == Container.TOP_LEVEL || c == Container.CONCRETE_CLASS) {
                        out += Diagnostic(r.start, r.end, DiagnosticSeverity.Error, "Property must be initialized", code = DiagnosticCodes.KT_MUST_BE_INITIALIZED)
                    }
                }
            }
        }
    }

    private fun redundantNullAssertions(text: String, inCode: BooleanArray, out: MutableList<Diagnostic>) {
        var i = 0
        while (i < text.length - 1) {
            if (!inCode[i]) { i++; continue }
            if (text[i] == '!' && text[i + 1] == '!') {
                if (provablyNonNullBefore(text, inCode, i)) {
                    out += Diagnostic(i, i + 2, DiagnosticSeverity.Warning, "Redundant non-null assertion ('!!') on a non-null value", code = DiagnosticCodes.KT_REDUNDANT_NOT_NULL)
                }
                i += 2
            } else if (text[i] == '?' && text[i + 1] == '.') {
                if (provablyNonNullBefore(text, inCode, i)) {
                    out += Diagnostic(i, i + 2, DiagnosticSeverity.Warning, "Redundant safe call ('?.') on a non-null receiver", code = DiagnosticCodes.KT_REDUNDANT_SAFE_CALL)
                }
                i += 2
            } else i++
        }
    }

    private fun provablyNonNullBefore(text: String, inCode: BooleanArray, opStart: Int): Boolean {
        var j = opStart - 1
        while (j >= 0 && (text[j] == ' ' || text[j] == '\t')) j--
        if (j < 0) return false
        if ((text[j] == '"' || text[j] == '\'') && !inCode[j]) return true
        if (!inCode[j]) return false
        var s = j
        while (s >= 0 && inCode[s] && (text[s].isLetterOrDigit() || text[s] == '_')) s--
        val word = text.substring(s + 1, j + 1)
        if (word.isEmpty()) return false
        if (word == "this" || word == "true" || word == "false") return true
        return word.all { it.isDigit() }
    }

    private fun unusedParameters(text: String, inCode: BooleanArray, out: MutableList<Diagnostic>) {
        var i = 0
        while (i < text.length) {
            if (inCode[i] && text.startsWith("fun", i) && wordBoundaryBefore(text, i) && wordBoundaryAfter(text, i + 3)) {
                unusedParametersForFun(text, inCode, i, out)
                i += 3
            } else i++
        }
    }

    private fun unusedParametersForFun(text: String, inCode: BooleanArray, funKw: Int, out: MutableList<Diagnostic>) {
        var p = funKw + 3
        while (p < text.length && text[p] != '(' && text[p] != '{' && text[p] != '\n') p++
        if (p >= text.length || text[p] != '(') return
        val paramsStart = p
        val paramsEnd = matchDelimiter(text, p, '(', ')') ?: return
        var b = paramsEnd + 1
        while (b < text.length && text[b] != '{' && text[b] != '=' && text[b] != '\n') b++
        if (b >= text.length || text[b] != '{') return
        val bodyEnd = matchDelimiter(text, b, '{', '}') ?: text.length
        if (hasModifierBefore(text, funKw, UNUSED_PARAM_EXEMPT)) return
        val name = readIdentifierAt(text, skipSpace(text, funKw + 3))
        if (name == "main") return
        if (enclosingContainer(text, funKw, inCode) == Container.INTERFACE) return
        val span = text.substring(paramsStart, bodyEnd + 1)
        for (param in splitParams(text.substring(paramsStart + 1, paramsEnd))) {
            val pn = param.name ?: continue
            if (pn == "_" || param.valVar || param.annotated) continue
            if (countWord(span, pn) <= 1) {
                out += Diagnostic(param.nameStart(paramsStart + 1), param.nameEnd(paramsStart + 1), DiagnosticSeverity.Warning, "Parameter '$pn' is never used", code = DiagnosticCodes.KT_UNUSED_PARAMETER)
            }
        }
    }

    private class ParamInfo(val name: String?, val relStart: Int, val valVar: Boolean, val annotated: Boolean) {
        fun nameStart(base: Int) = base + relStart
        fun nameEnd(base: Int) = base + relStart + (name?.length ?: 0)
    }

    private fun splitParams(params: String): List<ParamInfo> {
        val out = ArrayList<ParamInfo>()
        var depth = 0
        var start = 0
        fun emit(from: Int, to: Int) {
            val seg = params.substring(from, to)
            val t = seg.trim()
            if (t.isEmpty()) return
            val annotated = t.startsWith("@")
            val valVar = Regex("""^(val|var)\b""").containsMatchIn(t)
            var body = t
            if (annotated) {
                body = body.drop(1).trimStart().dropWhile { it.isLetterOrDigit() || it == '_' || it == '.' }.trimStart()
                if (body.startsWith("(")) { val c = body.indexOf(')'); if (c >= 0) body = body.substring(c + 1).trimStart() }
            }
            body = Regex("""^(val|var)\b\s*""").replace(body, "")
            body = Regex("""^(vararg|noinline|crossinline)\b\s*""").replace(body, "")
            val nm = body.takeWhile { it.isLetterOrDigit() || it == '_' }
            val rel = if (nm.isEmpty()) from else from + seg.indexOf(nm).coerceAtLeast(0)
            out += ParamInfo(nm.ifEmpty { null }, rel, valVar, annotated)
        }
        for (k in params.indices) {
            when (params[k]) {
                '(', '[', '<', '{' -> depth++
                ')', ']', '>', '}' -> if (depth > 0) depth--
                ',' -> if (depth == 0) { emit(start, k); start = k + 1 }
            }
        }
        emit(start, params.length)
        return out
    }


    private enum class Container { TOP_LEVEL, CONCRETE_CLASS, INTERFACE, NON_CONCRETE, FUNCTION, OTHER }

    private fun enclosingContainer(text: String, offset: Int, inCode: BooleanArray): Container {
        var depth = 0
        var i = offset - 1
        while (i >= 0) {
            if (inCode[i]) {
                when (text[i]) {
                    '}' -> depth++
                    '{' -> if (depth == 0) return classifyHeader(text, i) else depth--
                }
            }
            i--
        }
        return Container.TOP_LEVEL
    }

    private fun classifyHeader(text: String, brace: Int): Container {
        val from = maxOf(0, brace - 200)
        val header = text.substring(from, brace)
        fun lastWord(w: String): Int {
            var idx = -1
            var p = header.indexOf(w)
            while (p >= 0) {
                val b = if (p > 0) header[p - 1] else ' '
                val a = if (p + w.length < header.length) header[p + w.length] else ' '
                if (!(b.isLetterOrDigit() || b == '_') && !(a.isLetterOrDigit() || a == '_')) idx = p
                p = header.indexOf(w, p + 1)
            }
            return idx
        }
        val funIdx = lastWord("fun")
        val classIdx = lastWord("class")
        val ifaceIdx = lastWord("interface")
        val objIdx = lastWord("object")
        val maxIdx = maxOf(funIdx, classIdx, ifaceIdx, objIdx)
        if (maxIdx < 0) return Container.FUNCTION
        return when (maxIdx) {
            funIdx -> Container.FUNCTION
            ifaceIdx -> Container.INTERFACE
            objIdx -> Container.CONCRETE_CLASS
            else -> {
                val pre = header.substring(0, classIdx).takeLast(48)
                if (Regex("""\b(abstract|sealed|expect|annotation)\b""").containsMatchIn(pre)) Container.NON_CONCRETE
                else Container.CONCRETE_CLASS
            }
        }
    }

    private val UNUSED_PARAM_EXEMPT = setOf("override", "open", "abstract", "operator", "external", "expect", "actual")

    private fun hasModifierBefore(text: String, before: Int, modifiers: Set<String>): Boolean {
        val lineStart = text.lastIndexOf('\n', before - 1) + 1
        val head = text.substring(lineStart, before)
        return modifiers.any { Regex("""\b$it\b""").containsMatchIn(head) }
    }

    private fun hasPlainAssignment(s: String): Boolean = Regex("""(?<![=!<>])=(?![=>])""").containsMatchIn(s)

    private fun typeAfterColon(decl: String): String? {
        val colon = decl.indexOf(':')
        if (colon < 0) return null
        var end = decl.length
        val eq = Regex("""(?<![=!<>])=(?![=>])""").find(decl, colon)
        if (eq != null) end = eq.range.first
        return decl.substring(colon + 1, end).trim().ifEmpty { null }
    }

    private fun lineEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (c == '\n' || c == ';' || c == '{' || c == '}') return i
            i++
        }
        return text.length
    }

    private fun firstNonSpaceIndex(text: String, from: Int): Int {
        var j = from
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        return if (j < text.length) j else -1
    }

    private fun skipSpace(text: String, from: Int): Int {
        var j = from
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        return j
    }

    private fun readIdentifierAt(text: String, from: Int): String {
        var j = from
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
        return text.substring(from, j)
    }

    private fun wordBoundaryBefore(text: String, i: Int): Boolean = i == 0 || !(text[i - 1].isLetterOrDigit() || text[i - 1] == '_')
    private fun wordBoundaryAfter(text: String, i: Int): Boolean = i >= text.length || !(text[i].isLetterOrDigit() || text[i] == '_')

    private fun matchDelimiter(text: String, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (c == open) depth++ else if (c == close) { depth--; if (depth == 0) return i }
            i++
        }
        return null
    }

    private fun countWord(text: String, word: String): Int {
        var count = 0
        var i = text.indexOf(word)
        while (i >= 0) {
            val before = if (i > 0) text[i - 1] else ' '
            val after = if (i + word.length < text.length) text[i + word.length] else ' '
            if (!(before.isLetterOrDigit() || before == '_') && !(after.isLetterOrDigit() || after == '_')) count++
            i = text.indexOf(word, i + word.length)
        }
        return count
    }

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
        "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
        "val", "var", "when", "while", "import", "typeof",
    )
    private val KOTLIN_SOFT_KEYWORDS = setOf(
        "it", "field", "value", "set", "get", "by", "where", "init", "companion", "constructor",
        "dynamic", "reified", "vararg", "noinline", "crossinline", "expect", "actual", "override",
        "private", "public", "internal", "protected", "abstract", "final", "open", "sealed", "data",
        "enum", "annotation", "inner", "lateinit", "const", "suspend", "operator", "infix", "inline",
        "tailrec", "external", "out", "catch", "finally",
    )

    private val BUILTINS = setOf(
        "println", "print", "readLine", "listOf", "mutableListOf", "arrayListOf", "setOf", "mutableSetOf",
        "hashSetOf", "mapOf", "mutableMapOf", "hashMapOf", "linkedMapOf", "arrayOf", "emptyList", "emptyMap",
        "emptySet", "emptyArray", "require", "requireNotNull", "check", "checkNotNull", "error", "TODO",
        "run", "let", "apply", "also", "with", "use", "lazy", "repeat", "buildList", "buildString", "buildMap",
        "maxOf", "minOf", "listOfNotNull", "sequenceOf", "generateSequence", "to", "coroutineScope",
        "runBlocking", "launch", "async", "delay", "withContext", "flow", "flowOf", "runCatching",
        "String", "CharSequence", "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char",
        "Number", "Any", "Unit", "Nothing", "List", "MutableList", "Map", "MutableMap", "Set", "MutableSet",
        "Collection", "Iterable", "Sequence", "Array", "IntArray", "Pair", "Triple", "Comparable", "Result",
        "Exception", "RuntimeException", "IllegalStateException", "IllegalArgumentException", "Throwable",
        "StringBuilder", "Regex", "Comparator", "Enum", "Function",
        "Composable", "Modifier", "remember", "mutableStateOf", "derivedStateOf", "rememberCoroutineScope",
        "Text", "Button", "TextButton", "OutlinedButton", "Icon", "IconButton", "Image", "Column", "Row",
        "Box", "Spacer", "Surface", "Scaffold", "Card", "LazyColumn", "LazyRow", "MaterialTheme", "Divider",
        "TextField", "OutlinedTextField", "Checkbox", "Switch", "Slider", "CircularProgressIndicator",
        "Arrangement", "Alignment", "dp", "sp", "Color", "setContent", "collectAsState",
        "Log", "Bundle", "Context", "Intent", "View", "Activity", "Fragment", "Toast", "Application",
        "SavedStateHandle", "LiveData", "ViewModel", "Uri", "Handler", "Looper",
    )

    private fun scanString(text: String, open: Int, out: MutableList<Diagnostic>): Int {
        var i = open + 1
        val n = text.length
        while (i < n) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '\n' -> {
                    out.add(Diagnostic(open, i, DiagnosticSeverity.Error, "unterminated string literal", code = DiagnosticCodes.KT_SYNTAX))
                    return i
                }
                else -> i++
            }
        }
        out.add(Diagnostic(open, n, DiagnosticSeverity.Error, "unterminated string literal", code = DiagnosticCodes.KT_SYNTAX))
        return n
    }

    private fun scanChar(text: String, open: Int, out: MutableList<Diagnostic>): Int {
        var i = open + 1
        val n = text.length
        while (i < n) {
            when (text[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                '\n' -> {
                    out.add(Diagnostic(open, i, DiagnosticSeverity.Error, "unterminated character literal", code = DiagnosticCodes.KT_SYNTAX))
                    return i
                }
                else -> i++
            }
        }
        out.add(Diagnostic(open, n, DiagnosticSeverity.Error, "unterminated character literal", code = DiagnosticCodes.KT_SYNTAX))
        return n
    }

    private fun unusedImports(text: String): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()
        val lines = text.split('\n')
        var offset = 0
        for (line in lines) {
            val trimmed = line.trimStart()
            val lead = line.length - trimmed.length
            if (trimmed.startsWith("import ")) {
                val path = trimmed.removePrefix("import ").substringBefore(';').trim()
                if (path.isNotEmpty() && !path.endsWith("*") && !path.endsWith(".")) {
                    val simple = path.substringAfterLast('.').substringAfterLast(' ')
                    if (simple.isNotEmpty() && simple[0].isLetter() && !referencedOutsideImports(text, simple)) {
                        val start = offset + lead
                        out.add(Diagnostic(start, start + trimmed.length, DiagnosticSeverity.Warning, "Unused import directive", muted = true, code = DiagnosticCodes.KT_UNUSED_IMPORT))
                    }
                }
            }
            offset += line.length + 1
        }
        return out
    }

    private fun referencedOutsideImports(text: String, name: String): Boolean {
        var count = 0
        var i = text.indexOf(name)
        while (i >= 0) {
            val before = if (i > 0) text[i - 1] else ' '
            val after = if (i + name.length < text.length) text[i + name.length] else ' '
            val wholeWord = !isIdent(before) && !isIdent(after)
            if (wholeWord) {
                val lineStart = text.lastIndexOf('\n', i - 1) + 1
                val lineHead = text.substring(lineStart, i).trimStart()
                if (!lineHead.startsWith("import ")) count++
            }
            i = text.indexOf(name, i + name.length)
        }
        return count > 0
    }

    private fun matches(open: Char, close: Char): Boolean =
        (open == '(' && close == ')') || (open == '[' && close == ']') || (open == '{' && close == '}')
    private fun closeOf(open: Char): Char = when (open) { '(' -> ')'; '[' -> ']'; else -> '}' }
    private fun isIdent(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
