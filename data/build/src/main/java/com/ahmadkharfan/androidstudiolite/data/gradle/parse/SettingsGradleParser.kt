package com.ahmadkharfan.androidstudiolite.data.gradle.parse

import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedSettings

object SettingsGradleParser {

    fun parse(text: CharSequence): ParsedSettings {
        val tokens = GradleScriptScanner.tokenize(text)
        val modulePaths = LinkedHashSet<String>()
        val dirOverrides = LinkedHashMap<String, String>()
        var rootName: String? = null

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.type == GTokenType.IDENT) {
                when (t.text) {
                    "include" -> {


                        collectStatementStrings(tokens, i + 1).forEach { s ->
                            if (s.startsWith(":")) modulePaths += s
                        }
                    }
                    "rootProject" -> {

                        val eq = indexOfEqAfter(tokens, i)
                        if (eq != null) {
                            tokens.getOrNull(eq + 1)?.let { if (it.type == GTokenType.STRING) rootName = it.stringValue() }
                        }
                    }
                    "project" -> {

                        val path = tokens.getOrNull(i + 1)?.takeIf { it.type == GTokenType.LPAREN }
                            ?.let { tokens.getOrNull(i + 2) }
                            ?.takeIf { it.type == GTokenType.STRING }?.stringValue()
                        if (path != null) {
                            val eq = indexOfEqAfter(tokens, i)
                            val fileArg = eq?.let { firstStringInCall(tokens, it + 1) }
                            if (fileArg != null) dirOverrides[path] = fileArg
                        }
                    }
                }
            }
            i++
        }
        return ParsedSettings(rootName, modulePaths.toList(), dirOverrides)
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
}
