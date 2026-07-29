package com.ahmadkharfan.androidstudiolite.navigation

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Project ids come straight from directory names (`AndroidProjectRepository`, `id = dir.name`) with
 * no character sanitisation, so they can contain `?`, `#` and `&` — all legal in a directory name
 * and all structural in a route string.
 */
class RoutesTest {

    @Test
    fun `editor escapes characters that would end the path segment`() {
        assertEquals("editor/my%3Fapp", Routes.editor("my?app"))
        assertEquals("editor/my%23app", Routes.editor("my#app"))
        assertEquals("editor/a%26b", Routes.editor("a&b"))
    }

    @Test
    fun `editor round trips ids through the pattern`() {
        for (projectId in HOSTILE_PROJECT_IDS) {
            assertEquals(
                "round trip failed for '$projectId'",
                projectId,
                matchPathArg(Routes.EDITOR_PATTERN, Routes.editor(projectId), "projectId"),
            )
        }
    }

    @Test
    fun `editor leaves ordinary ids readable`() {
        assertEquals("editor/MyApp-1.2_x~y", Routes.editor("MyApp-1.2_x~y"))
    }

    @Test
    fun `git routes round trip the same ids as editor`() {
        for (projectId in HOSTILE_PROJECT_IDS) {
            val routes = mapOf(
                Routes.GIT_HISTORY_PATTERN to Routes.gitHistory(projectId),
                Routes.GIT_BLAME_PATTERN to Routes.gitBlame(projectId, "src/Main.kt"),
                Routes.GIT_REFS_PATTERN to Routes.gitRefs(projectId, "BRANCHES"),
                Routes.GIT_CONFLICTS_PATTERN to Routes.gitConflicts(projectId),
                Routes.GIT_DIFF_PATTERN to
                    Routes.gitDiff(projectId, "src/Main.kt", GitDiffTarget.INDEX_TO_WORKTREE),
            )
            for ((pattern, route) in routes) {
                assertEquals(
                    "round trip failed for '$projectId' on $pattern",
                    projectId,
                    matchPathArg(pattern, route, "projectId"),
                )
            }
        }
    }

    @Test
    fun `blockingError escapes its argument`() {
        assertEquals("blockingError/sdcard", Routes.blockingError("sdcard"))
        assertEquals("blockingError/a%3Fb", Routes.blockingError("a?b"))
    }

    /**
     * Mirrors how Navigation resolves a path argument: the route is cut at the first `?` or `#`,
     * the remaining path is matched against the pattern with `{arg}` as `([^/]+?)`, and the matched
     * group is percent-decoded (`NavDeepLink.getMatchingPathArguments` → `Uri.decode`). Returns null
     * when the destination would not match at all.
     */
    private fun matchPathArg(pattern: String, route: String, argName: String): String? {
        val path = route.substringBefore('?').substringBefore('#')
        val argNames = ARG_PLACEHOLDER.findAll(pattern.substringBefore('?')).map { it.groupValues[1] }.toList()
        val regex = ARG_PLACEHOLDER.split(pattern.substringBefore('?'))
            .joinToString(separator = "([^/]+?)") { Regex.escape(it) }
            .toRegex()
        val groups = regex.matchEntire(path)?.groupValues ?: return null
        val index = argNames.indexOf(argName)
        return percentDecode(groups[index + 1])
    }

    private fun percentDecode(value: String): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                bytes.write(value.substring(index + 1, index + 3).toInt(radix = 16))
                index += 3
            } else {
                bytes.write(char.code)
                index++
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        val ARG_PLACEHOLDER = Regex("""\{(\w+)}""")

        val HOSTILE_PROJECT_IDS = listOf(
            "my?app",
            "my#app",
            "a&b",
            "a b",
            "100%done",
            "a=b",
            "a/b",
            "café ☕",
            "plain",
        )
    }
}
