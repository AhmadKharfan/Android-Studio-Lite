/**
 * Rules for contract (`:*:api`) modules, kept free of Gradle types so they can be unit tested.
 *
 * The rule here exists because of a real crash. `GitPanelApi.Panel` was declared with default
 * argument values. For a `@Composable` interface member the Compose compiler emits a
 * `ComposeDefaultImpls.<name>$default` bridge that invokes the abstract method through a synthesised
 * signature. While the interface and its implementation shared a module that resolved; once the
 * interface moved to its own api module and the implementation stayed behind, the bridge no longer
 * matched the override. It compiled, passed detekt, passed every unit test, and threw
 * `AbstractMethodError` the first time the panel was composed.
 *
 * Nothing on the JVM side can see that, so it is checked at the source level instead.
 */
data class ApiContractViolation(
    val file: String,
    val line: Int,
    val declaration: String,
) {
    fun render(): String =
        "  $file:$line\n      `$declaration` is a @Composable interface member with default argument " +
            "values. Across a module boundary the generated defaults bridge does not match the " +
            "implementation, which fails at runtime with AbstractMethodError. Require every argument."
}

/**
 * @param sources file path to file contents. Only contract modules should be passed in.
 */
fun findApiContractViolations(sources: Map<String, String>): List<ApiContractViolation> =
    sources.flatMap { (path, text) -> violationsIn(path, text) }

private fun violationsIn(path: String, text: String): List<ApiContractViolation> {
    val found = mutableListOf<ApiContractViolation>()
    val lines = text.lines()
    var interfaceDepth = -1
    var braceDepth = 0
    var composableSeen = false

    var index = 0
    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.substringBefore("//").trim()

        if (interfaceDepth < 0 && Regex("""\binterface\s+\w+""").containsMatchIn(line)) {
            interfaceDepth = braceDepth
        }
        if (line.startsWith("@Composable")) composableSeen = true

        if (composableSeen && interfaceDepth >= 0 && Regex("""\bfun\s+\w+""").containsMatchIn(line)) {
            val (signature, consumed) = readSignature(lines, index)
            if (hasDefaultArgument(signature)) {
                val name = Regex("""\bfun\s+(\w+)""").find(signature)?.groupValues?.get(1).orEmpty()
                found += ApiContractViolation(path, index + 1, "fun $name(...)")
            }
            index += consumed
            composableSeen = false
            continue
        }

        if (line.isNotEmpty() && !line.startsWith("@")) {
            braceDepth += line.count { it == '{' } - line.count { it == '}' }
            if (interfaceDepth >= 0 && braceDepth <= interfaceDepth) interfaceDepth = -1
        }
        index++
    }
    return found
}

/** Collects a possibly multi-line signature up to the closing paren of its parameter list. */
private fun readSignature(lines: List<String>, start: Int): Pair<String, Int> {
    val builder = StringBuilder()
    var depth = 0
    var consumed = 0
    var opened = false
    for (i in start until lines.size) {
        val line = lines[i].substringBefore("//")
        builder.append(line).append('\n')
        consumed++
        depth += line.count { it == '(' } - line.count { it == ')' }
        if (line.contains('(')) opened = true
        if (opened && depth <= 0) break
    }
    return builder.toString() to consumed
}

/** True when the parameter list contains a top-level `=`, i.e. a default value. */
private fun hasDefaultArgument(signature: String): Boolean {
    val params = signature.substringAfter('(', "").substringBeforeLast(')', "")
    var depth = 0
    var index = 0
    while (index < params.length) {
        when (params[index]) {
            '(', '<', '[' -> depth++
            ')', '>', ']' -> depth--
            '-' -> if (index + 1 < params.length && params[index + 1] == '>') index++ // lambda arrow
            '=' -> {
                val next = params.getOrNull(index + 1)
                // `=` that is not part of `==`, `>=`, `<=` or `->`
                if (depth == 0 && next != '=' && params.getOrNull(index - 1) !in listOf('!', '<', '>', '=')) {
                    return true
                }
            }
        }
        index++
    }
    return false
}
