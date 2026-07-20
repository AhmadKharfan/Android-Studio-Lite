package com.ahmadkharfan.androidstudiolite.feature.editor

class EditorFindController {
    fun closedBar(): FindBarSnapshot = FindBarSnapshot(
        findBarOpen = false,
        findQuery = "",
        findMatchCount = 0,
        findCurrentMatch = 0,
    )

    fun toggledBar(currentlyOpen: Boolean): FindBarSnapshot =
        if (currentlyOpen) closedBar() else FindBarSnapshot(findBarOpen = true)

    fun queryChanged(text: String, query: String): FindQuerySnapshot {
        val count = if (query.isEmpty()) 0 else countOccurrences(text, query)
        return FindQuerySnapshot(
            findQuery = query,
            findMatchCount = count,
            findCurrentMatch = if (count > 0) 1 else 0,
        )
    }

    fun nextMatch(matchCount: Int, currentMatch: Int): Int =
        if (matchCount == 0) 0 else (currentMatch % matchCount) + 1

    fun previousMatch(matchCount: Int, currentMatch: Int): Int =
        if (matchCount == 0) 0 else ((currentMatch - 2 + matchCount) % matchCount) + 1

    fun countOccurrences(text: String, query: String): Int {
        var count = 0
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            count++
            index = text.indexOf(query, index + 1, ignoreCase = true)
        }
        return count
    }
}

data class FindBarSnapshot(
    val findBarOpen: Boolean = false,
    val findQuery: String = "",
    val findMatchCount: Int = 0,
    val findCurrentMatch: Int = 0,
)

data class FindQuerySnapshot(
    val findQuery: String,
    val findMatchCount: Int,
    val findCurrentMatch: Int,
)
