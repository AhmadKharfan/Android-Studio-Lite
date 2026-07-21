package com.ahmadkharfan.androidstudiolite.feature.editor
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslCodeLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslCodeSpan
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLineGit
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslFileIcons
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.SyntaxLexer
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.SyntaxToken
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.toAslSyntaxColor
fun highlightToLines(
    text: String,
    language: EditorLanguage,
    gitLineStatus: Map<Int, AslLineGit> = emptyMap(),
    breakpoints: Set<Int> = emptySet(),
    activeLine: Int = -1,
): List<AslCodeLine> {
    val lexer = SyntaxLexer.forLanguage(language)
    var state = lexer.initialState
    val rawLines = text.split('\n')
    return rawLines.mapIndexed { index, lineText ->
        val result = lexer.tokenizeLine(lineText, state)
        state = result.endState
        AslCodeLine(
            spans = buildSpans(lineText, result.tokens),
            git = gitLineStatus[index],
            breakpoint = index in breakpoints,
            active = index == activeLine,
        )
    }
}
private fun buildSpans(line: String, tokens: List<SyntaxToken>): List<AslCodeSpan> {
    if (line.isEmpty()) return listOf(AslCodeSpan("", null))
    val spans = ArrayList<AslCodeSpan>(tokens.size * 2 + 1)
    var cursor = 0
    for (token in tokens.sortedBy { it.start }) {
        val start = token.start.coerceIn(0, line.length)
        val end = token.end.coerceIn(start, line.length)
        if (start > cursor) spans.add(AslCodeSpan(line.substring(cursor, start), null))
        spans.add(AslCodeSpan(line.substring(start, end), token.type.toAslSyntaxColor()))
        cursor = end
    }
    if (cursor < line.length) spans.add(AslCodeSpan(line.substring(cursor), null))
    return spans
}

fun fileIconFor(name: String): String = AslFileIcons.iconFor(name)
