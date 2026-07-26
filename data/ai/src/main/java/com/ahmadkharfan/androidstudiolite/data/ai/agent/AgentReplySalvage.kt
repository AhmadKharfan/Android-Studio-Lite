package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction

internal object AgentReplySalvage {

    private val LOOSE_FILE_TOOL = Regex(""""tool"\s*:\s*"(create_file|edit_file)"""")
    private val LOOSE_PATH = Regex(""""path"\s*:\s*"((?:\\.|[^"\\])*)"""")

    fun salvageActions(raw: String): List<AgentAction> {
        val actions = LinkedHashMap<String, AgentAction>()
        var index = 0
        while (index < raw.length) {
            val toolIndex = raw.indexOf("\"tool\"", index)
            if (toolIndex < 0) break
            var start = toolIndex
            while (start > 0 && raw[start] != '{') start--
            if (raw.getOrNull(start) != '{') {
                index = toolIndex + 6
                continue
            }
            val action = AgentReplyParser.extractBalancedObject(raw, start)
                ?.let(AgentReplyParser::parseJsonObject)
                ?.let(AgentReplyParser::toActionOrNull)
                ?: salvageLooseFileAction(raw, start)
            action?.let { AgentReplyParser.putAction(actions, it) }
            index = toolIndex + 6
        }
        return actions.values.toList()
    }

    private fun salvageLooseFileAction(raw: String, objectStart: Int): AgentAction? {
        if (objectStart < 0 || objectStart >= raw.length) return null
        val slice = raw.substring(objectStart)
        val toolMatch = LOOSE_FILE_TOOL.find(slice) ?: return null
        val tool = toolMatch.groupValues[1]
        val absoluteToolIndex = objectStart + toolMatch.range.first
        val pathMatch = LOOSE_PATH.find(slice, toolMatch.range.last + 1) ?: return null
        val path = unescapeJsonString(pathMatch.groupValues[1])
        val contentKeyIndex = slice.indexOf("\"content\"", pathMatch.range.last)
        if (contentKeyIndex < 0) return null
        val openQuoteIndex = slice.indexOf('"', slice.indexOf(':', contentKeyIndex) + 1)
        if (openQuoteIndex < 0) return null
        val contentStart = objectStart + openQuoteIndex + 1
        val searchEnd = looseContentSearchEnd(raw, absoluteToolIndex)
        val content = extractLooseJsonStringValue(raw, contentStart, searchEnd)
            ?.let { AgentContentSanitizer.sanitizeFileContent(path, it) }
            ?: return null
        AiAgentLog.i("Parse", "salvageLooseFileAction tool=$tool path=$path contentLen=${content.length}")
        return when (tool) {
            "create_file" -> AgentAction.CreateFile(path, content)
            "edit_file" -> AgentAction.EditFile(path, content)
            else -> null
        }
    }

    fun extractLooseJsonStringValue(
        raw: String,
        contentStart: Int,
        searchEnd: Int = raw.length,
    ): String? {
        if (contentStart < 0 || contentStart >= raw.length) return null
        val endBound = searchEnd.coerceIn(contentStart, raw.length)
        val slice = raw.substring(contentStart, endBound)
        val endPatterns = listOf("\"},", "\"}]", "\"}}]", "\"}]}", "\"}}}", "\"}}", "\"}")
        var end = -1
        for (pattern in endPatterns) {
            val index = slice.indexOf(pattern)
            if (index >= 0 && (end < 0 || index < end)) {
                end = index
            }
        }
        if (end < 0) return null
        return unescapeJsonString(slice.substring(0, end))
    }

    fun repairJsonStringEscapes(source: String): String {
        val output = StringBuilder(source.length + 32)
        var inString = false
        var escaped = false
        for (character in source) {
            when {
                escaped -> {
                    output.append(character)
                    escaped = false
                }
                character == '\\' && inString -> {
                    output.append(character)
                    escaped = true
                }
                character == '"' -> {
                    output.append(character)
                    inString = !inString
                }
                inString && character == '\n' -> output.append("\\n")
                inString && character == '\r' -> output.append("\\r")
                inString && character == '\t' -> output.append("\\t")
                else -> output.append(character)
            }
        }
        return output.toString()
    }

    fun extractThought(raw: String): String? {
        val root = AgentReplyParser.parseRootObject(raw) ?: return null
        return root["thought"]?.let(AgentReplyParser::stringOrNull)?.takeIf { it.isNotBlank() }
    }

    private fun looseContentSearchEnd(raw: String, toolKeyIndex: Int): Int {
        val nextTool = raw.indexOf("\"tool\"", toolKeyIndex + 6)
        return if (nextTool > toolKeyIndex) nextTool else raw.length
    }

    private fun unescapeJsonString(source: String): String = buildString(source.length) {
        var index = 0
        while (index < source.length) {
            if (source[index] == '\\' && index + 1 < source.length) {
                when (source[index + 1]) {
                    'n' -> {
                        append('\n')
                        index += 2
                    }
                    'r' -> {
                        append('\r')
                        index += 2
                    }
                    't' -> {
                        append('\t')
                        index += 2
                    }
                    '"' -> {
                        append('"')
                        index += 2
                    }
                    '\\' -> {
                        append('\\')
                        index += 2
                    }
                    else -> {
                        append(source[index])
                        index++
                    }
                }
            } else {
                append(source[index])
                index++
            }
        }
    }
}
