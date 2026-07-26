package com.ahmadkharfan.androidstudiolite.data.gradle.parse

import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedSettings

object SettingsGradleParser {

    fun parse(text: CharSequence): ParsedSettings {
        val tokens = GradleScriptScanner.tokenize(text)
        val settings = ParsedSettingsState()
        tokens.forEachIndexed { index, token ->
            if (token.type == GTokenType.IDENT) readStatement(tokens, index, token.text, settings)
        }
        return ParsedSettings(settings.rootName, settings.modulePaths.toList(), settings.dirOverrides)
    }

    private fun readStatement(
        tokens: List<GToken>,
        index: Int,
        name: String,
        settings: ParsedSettingsState,
    ) {
        when (name) {
            "include" -> collectStatementStrings(tokens, index + 1)
                .filterTo(settings.modulePaths) { it.startsWith(":") }
            "rootProject" -> rootNameAt(tokens, index)?.let { settings.rootName = it }
            "project" -> projectDirOverrideAt(tokens, index)?.let { (path, directory) ->
                settings.dirOverrides[path] = directory
            }
        }
    }

    private fun rootNameAt(tokens: List<GToken>, index: Int): String? {
        val equals = indexOfEqAfter(tokens, index) ?: return null
        return tokens.getOrNull(equals + 1)
            ?.takeIf { it.type == GTokenType.STRING }
            ?.stringValue()
    }

    private fun projectDirOverrideAt(tokens: List<GToken>, index: Int): Pair<String, String>? {
        val path = tokens.getOrNull(index + 1)?.takeIf { it.type == GTokenType.LPAREN }
            ?.let { tokens.getOrNull(index + 2) }
            ?.takeIf { it.type == GTokenType.STRING }
            ?.stringValue()
            ?: return null
        val equals = indexOfEqAfter(tokens, index) ?: return null
        val directory = firstStringInCall(tokens, equals + 1) ?: return null
        return path to directory
    }

    private fun collectStatementStrings(tokens: List<GToken>, from: Int): List<String> {
        val result = ArrayList<String>()
        var i = from
        var sawContent = false
        while (i < tokens.size) {
            val t = tokens[i]
            when (t.type) {
                GTokenType.STRING -> { result += t.stringValue(); sawContent = true }
                GTokenType.NEWLINE -> if (sawContent) return result
                GTokenType.LPAREN, GTokenType.COMMA -> {}
                GTokenType.RPAREN -> return result
                else -> if (sawContent) return result
            }
            i++
        }
        return result
    }

    private fun indexOfEqAfter(tokens: List<GToken>, from: Int): Int? {
        var i = from
        while (i < tokens.size) {
            when (tokens[i].type) {
                GTokenType.EQ -> return i
                GTokenType.NEWLINE -> return null
                else -> {}
            }
            i++
        }
        return null
    }

    private fun firstStringInCall(tokens: List<GToken>, from: Int): String? {
        var i = from
        while (i < tokens.size && tokens[i].type != GTokenType.NEWLINE) {
            if (tokens[i].type == GTokenType.STRING) return tokens[i].stringValue()
            i++
        }
        return null
    }

    private data class ParsedSettingsState(
        var rootName: String? = null,
        val modulePaths: LinkedHashSet<String> = LinkedHashSet(),
        val dirOverrides: LinkedHashMap<String, String> = LinkedHashMap(),
    )
}
