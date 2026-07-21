package com.ahmadkharfan.androidstudiolite.data.gradle.parse

import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedAndroidBlock
import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedBuildScript
import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedPlugin
import com.ahmadkharfan.androidstudiolite.data.gradle.model.RawDependency
import com.ahmadkharfan.androidstudiolite.data.gradle.model.RawDependencyKind

object BuildGradleParser {

    private val KNOWN_CONFIGS = setOf(
        "implementation", "api", "compileOnly", "runtimeOnly", "annotationProcessor",
        "kapt", "ksp", "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
        "androidTestImplementation", "androidTestApi", "debugImplementation", "releaseImplementation",
        "lintChecks", "coreLibraryDesugaring",
    )
    private val CONFIG_SUFFIXES = listOf("Implementation", "Api", "CompileOnly", "RuntimeOnly", "AnnotationProcessor")

    fun parse(text: CharSequence, dsl: GradleDsl): ParsedBuildScript {
        val tokens = GradleScriptScanner.tokenize(text)
        return ParsedBuildScript(
            dsl = dsl,
            plugins = parsePlugins(tokens),
            android = parseAndroid(tokens),
            dependencies = parseDependencies(tokens),
        )
    }

    private fun looksLikeConfig(name: String): Boolean =
        name in KNOWN_CONFIGS || CONFIG_SUFFIXES.any { name.endsWith(it) && name.length > it.length }


    private fun parsePlugins(tokens: List<GToken>): List<ParsedPlugin> {
        val plugins = ArrayList<ParsedPlugin>()
        GradleScriptScanner.findBlockBody(tokens, "plugins")?.let { range ->
            plugins += parsePluginsBlock(tokens, range)
        }

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.type == GTokenType.IDENT && t.text == "apply") {

                val str = (i + 1 until minOf(i + 8, tokens.size))
                    .firstNotNullOfOrNull { tokens[it].takeIf { tk -> tk.type == GTokenType.STRING } }
                val isPlugin = (i + 1 until minOf(i + 6, tokens.size))
                    .any { tokens[it].type == GTokenType.IDENT && tokens[it].text == "plugin" }
                if (isPlugin && str != null) plugins += ParsedPlugin(str.stringValue())
            }
            i++
        }
        return plugins.distinctBy { it.id + "@" + it.version }
    }

    private fun parsePluginsBlock(tokens: List<GToken>, range: IntRange): List<ParsedPlugin> {
        val out = ArrayList<ParsedPlugin>()
        var i = range.first
        val end = range.last + 1
        while (i < end) {
            val t = tokens[i]
            if (t.type == GTokenType.IDENT) {
                when (t.text) {
                    "id" -> {
                        val id = firstStringArg(tokens, i + 1, end)
                        if (id != null) out += ParsedPlugin(id, versionAfter(tokens, i + 1, end))
                    }
                    "kotlin" -> {

                        val arg = firstStringArg(tokens, i + 1, end)
                        if (arg != null) out += ParsedPlugin("org.jetbrains.kotlin.$arg", versionAfter(tokens, i + 1, end))
                    }
                    "alias" -> {

                        val accessor = catalogAccessorAfter(tokens, i + 1, end)
                        if (accessor != null && accessor.startsWith("plugins.")) {
                            out += ParsedPlugin(accessor.removePrefix("plugins."), fromCatalog = true)
                        }
                    }
                }
            }
            i++
        }
        return out
    }

    private fun versionAfter(tokens: List<GToken>, from: Int, end: Int): String? {
        var i = from
        while (i < end && tokens[i].type != GTokenType.NEWLINE) {
            if (tokens[i].type == GTokenType.IDENT && tokens[i].text == "version") {
                return firstStringArg(tokens, i + 1, end)
            }
            i++
        }
        return null
    }


    private fun parseAndroid(tokens: List<GToken>): ParsedAndroidBlock? {
        val range = GradleScriptScanner.findBlockBody(tokens, "android") ?: return null
        val direct = readAssignments(tokens, range)
        val defaultConfig = GradleScriptScanner.findBlockBody(tokens, "defaultConfig", range.first, range.last + 1)
            ?.let { readAssignments(tokens, it) } ?: emptyMap()

        val buildTypes = GradleScriptScanner.findBlockBody(tokens, "buildTypes", range.first, range.last + 1)
            ?.let { GradleScriptScanner.childBlockNames(tokens, it) } ?: emptyList()
        val flavorBlocks = GradleScriptScanner.findBlockBody(tokens, "productFlavors", range.first, range.last + 1)
            ?.let { GradleScriptScanner.childBlocks(tokens, it) } ?: emptyList()
        val flavors = flavorBlocks.map { it.name }.distinct()
        val flavorDimensionOf = LinkedHashMap<String, String>()
        for (flavor in flavorBlocks) {
            readAssignments(tokens, flavor.body)["dimension"]?.let { flavorDimensionOf.putIfAbsent(flavor.name, it) }
        }
        val dimensions = GradleScriptScanner.findBlockBody(tokens, "flavorDimensions", range.first, range.last + 1)
            ?.let { GradleScriptScanner.childBlockNames(tokens, it) }
            ?: flavorDimensionCallArgs(tokens, range)

        fun pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { direct[it] ?: defaultConfig[it] }

        return ParsedAndroidBlock(
            namespace = pick("namespace"),
            compileSdk = pick("compileSdk", "compileSdkVersion") ?: compileSdkFromBlock(tokens, range),
            applicationId = pick("applicationId"),
            minSdk = pick("minSdk", "minSdkVersion"),
            targetSdk = pick("targetSdk", "targetSdkVersion"),
            versionCode = pick("versionCode"),
            versionName = pick("versionName"),
            buildTypes = buildTypes,
            flavorDimensions = dimensions,
            productFlavors = flavors,
            flavorDimensionOf = flavorDimensionOf,
        )
    }

    private fun compileSdkFromBlock(tokens: List<GToken>, range: IntRange): String? {
        val body = GradleScriptScanner.findBlockBody(tokens, "compileSdk", range.first, range.last + 1) ?: return null
        return (body.first..body.last).firstNotNullOfOrNull {
            tokens[it].takeIf { t -> t.type == GTokenType.NUMBER }?.text
        }
    }

    private fun flavorDimensionCallArgs(tokens: List<GToken>, range: IntRange): List<String> {
        var i = range.first
        val end = range.last + 1
        val result = ArrayList<String>()
        while (i < end) {
            if (tokens[i].type == GTokenType.IDENT && tokens[i].text == "flavorDimensions") {
                var j = i + 1
                while (j < end && tokens[j].type != GTokenType.NEWLINE && tokens[j].type != GTokenType.LBRACE) {
                    if (tokens[j].type == GTokenType.STRING) result += tokens[j].stringValue()
                    j++
                }
            }
            i++
        }
        return result.distinct()
    }

    private fun readAssignments(tokens: List<GToken>, range: IntRange): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var i = range.first
        val end = range.last + 1
        while (i < end) {
            val t = tokens[i]
            if (t.type == GTokenType.LBRACE) {
                val close = GradleScriptScanner.matchBrace(tokens, i, end) ?: break
                i = close + 1
                continue
            }
            if (t.type == GTokenType.IDENT) {
                val next = nextSignificant(tokens, i + 1, end)
                if (next != null) {
                    val nt = tokens[next]
                    when (nt.type) {
                        GTokenType.EQ -> valueToken(tokens, next + 1, end)?.let { map.putIfAbsent(t.text, it) }
                        GTokenType.LPAREN -> valueToken(tokens, next + 1, end)?.let { map.putIfAbsent(t.text, it) }
                        GTokenType.STRING, GTokenType.NUMBER ->
                            map.putIfAbsent(t.text, scalarText(nt))
                        else -> {}
                    }
                }
            }
            i++
        }
        return map
    }

    private fun valueToken(tokens: List<GToken>, from: Int, end: Int): String? {
        val idx = nextSignificant(tokens, from, end) ?: return null
        val tk = tokens[idx]
        return when (tk.type) {
            GTokenType.STRING, GTokenType.NUMBER -> scalarText(tk)
            else -> null
        }
    }

    private fun scalarText(t: GToken): String = if (t.type == GTokenType.STRING) t.stringValue() else t.text


    private fun parseDependencies(tokens: List<GToken>): List<RawDependency> {
        val range = GradleScriptScanner.findBlockBody(tokens, "dependencies") ?: return emptyList()
        val out = ArrayList<RawDependency>()
        var i = range.first
        val end = range.last + 1
        while (i < end) {
            val t = tokens[i]
            if (t.type == GTokenType.LBRACE) {

                val close = GradleScriptScanner.matchBrace(tokens, i, end) ?: break
                i = close + 1
                continue
            }
            if (t.type == GTokenType.IDENT && looksLikeConfig(t.text)) {
                val dep = parseDependencyLine(tokens, t.text, i + 1, end)
                if (dep != null) out += dep
            }
            i++
        }
        return out
    }

    private fun parseDependencyLine(tokens: List<GToken>, config: String, from: Int, end: Int): RawDependency? {
        val first = nextSignificant(tokens, from, end) ?: return null

        val (argStart, argEnd) = if (tokens[first].type == GTokenType.LPAREN) {
            val close = GradleScriptScanner.matchParen(tokens, first, end) ?: return null
            (first + 1) to close
        } else {
            first to statementEnd(tokens, first, end)
        }
        if (argStart >= argEnd) return null


        var isPlatform = false
        var innerStart = argStart
        val head = tokens[nextSignificant(tokens, argStart, argEnd) ?: argStart]
        if (head.type == GTokenType.IDENT) {
            when (head.text) {
                "platform", "enforcedPlatform" -> {
                    isPlatform = true
                    innerStart = unwrapCall(tokens, argStart, argEnd) ?: argStart
                }
                "project" -> {
                    val path = firstStringArg(tokens, argStart, argEnd)
                    return RawDependency(config, RawDependencyKind.PROJECT, coordinate = path)
                }
                "kotlin" -> {
                    val name = firstStringArg(tokens, argStart, argEnd)
                    return if (name != null)
                        RawDependency(config, RawDependencyKind.MODULE, coordinate = "org.jetbrains.kotlin:kotlin-$name")
                    else RawDependency(config, RawDependencyKind.UNKNOWN)
                }
                "files", "fileTree" -> return RawDependency(config, RawDependencyKind.UNKNOWN)
            }
        }


        val accessor = catalogAccessorAtLibs(tokens, innerStart, argEnd)
        if (accessor != null) {
            val kind = if (accessor.startsWith("bundles.")) RawDependencyKind.CATALOG_BUNDLE else RawDependencyKind.CATALOG
            return RawDependency(config, kind, catalogAccessor = "libs.$accessor", isPlatform = isPlatform)
        }


        val coord = firstStringArg(tokens, innerStart, argEnd)
        return when {
            coord != null -> RawDependency(config, RawDependencyKind.MODULE, coordinate = coord, isPlatform = isPlatform)
            else -> RawDependency(config, RawDependencyKind.UNKNOWN)
        }
    }

    private fun unwrapCall(tokens: List<GToken>, from: Int, end: Int): Int? {
        val id = nextSignificant(tokens, from, end) ?: return null
        val paren = nextSignificant(tokens, id + 1, end) ?: return null
        return if (tokens[paren].type == GTokenType.LPAREN) paren + 1 else null
    }


    private fun statementEnd(tokens: List<GToken>, from: Int, end: Int): Int {
        var i = from
        while (i < end && tokens[i].type != GTokenType.NEWLINE) i++
        return i
    }

    private fun nextSignificant(tokens: List<GToken>, from: Int, end: Int): Int? {
        var i = from
        while (i < end) {
            if (tokens[i].type != GTokenType.NEWLINE) return i
            i++
        }
        return null
    }

    private fun firstStringArg(tokens: List<GToken>, from: Int, end: Int): String? {
        var i = from
        var depth = 0
        while (i < end) {
            when (tokens[i].type) {
                GTokenType.STRING -> return tokens[i].stringValue()
                GTokenType.NEWLINE -> if (depth == 0) return null
                GTokenType.LPAREN -> depth++
                GTokenType.RPAREN -> if (depth == 0) return null else depth--
                else -> {}
            }
            i++
        }
        return null
    }

    private fun catalogAccessorAtLibs(tokens: List<GToken>, from: Int, end: Int): String? {
        val start = nextSignificant(tokens, from, end) ?: return null
        val head = tokens[start]
        if (head.type != GTokenType.IDENT || head.text != "libs") return null
        return readAccessorChain(tokens, start + 1, end)
    }

    private fun catalogAccessorAfter(tokens: List<GToken>, from: Int, end: Int): String? {
        var i = nextSignificant(tokens, from, end) ?: return null
        if (tokens[i].type == GTokenType.LPAREN) i = nextSignificant(tokens, i + 1, end) ?: return null
        if (tokens[i].type != GTokenType.IDENT || tokens[i].text != "libs") return null
        return readAccessorChain(tokens, i + 1, end)
    }

    private fun readAccessorChain(tokens: List<GToken>, from: Int, end: Int): String? {
        val segs = ArrayList<String>()
        var i = from
        while (i + 1 < end && tokens[i].type == GTokenType.DOT && tokens[i + 1].type == GTokenType.IDENT) {
            segs += tokens[i + 1].text
            i += 2
        }
        return if (segs.isEmpty()) null else segs.joinToString(".")
    }
}
