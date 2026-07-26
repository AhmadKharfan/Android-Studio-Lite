package com.ahmadkharfan.androidstudiolite.designsystem.component.content

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class ListBlock(val ordered: Boolean, val items: List<MdListItem>) : MdBlock
    data object HorizontalRule : MdBlock
}

data class MdListItem(val text: String, val checked: Boolean? = null)

object MarkdownParser {

    private data class ParseStep(
        val nextLine: Int,
        val block: MdBlock? = null,
    )

    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val ul = Regex("""^([-*+])\s+(.*)$""")
    private val ol = Regex("""^(\d+)\.\s+(.*)$""")
    private val task = Regex("""^\[([ xX])]\s+(.*)$""")
    private val hr = Regex("""^(-{3,}|\*{3,}|_{3,})\s*$""")
    private val fenceOpen = Regex("""^```(\w*)\s*$""")

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").split('\n')
        val blocks = ArrayList<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val step = parseBlock(lines, i)
            step.block?.let(blocks::add)
            i = step.nextLine
        }
        return blocks
    }

    private fun parseBlock(lines: List<String>, index: Int): ParseStep {
        val trimmed = lines[index].trim()
        return when {
            trimmed.isEmpty() -> ParseStep(index + 1)
            fenceOpen.matches(trimmed) -> parseCodeBlock(lines, index, trimmed)
            hr.matches(trimmed) -> ParseStep(index + 1, MdBlock.HorizontalRule)
            heading.matches(trimmed) -> parseHeading(index, trimmed)
            trimmed.startsWith(">") -> parseQuote(lines, index)
            ul.matches(trimmed) || ol.matches(trimmed) -> parseList(lines, index, trimmed)
            else -> parseParagraph(lines, index, trimmed)
        }
    }

    private fun parseCodeBlock(lines: List<String>, index: Int, openingFence: String): ParseStep {
        val language = fenceOpen.matchEntire(openingFence)!!.groupValues[1].ifBlank { "text" }
        val body = StringBuilder()
        var nextLine = index + 1
        while (nextLine < lines.size && !lines[nextLine].trim().startsWith("```")) {
            if (body.isNotEmpty()) body.append('\n')
            body.append(lines[nextLine])
            nextLine++
        }
        if (nextLine < lines.size) nextLine++
        return ParseStep(nextLine, MdBlock.Code(language, body.toString()))
    }

    private fun parseHeading(index: Int, trimmed: String): ParseStep {
        val match = heading.matchEntire(trimmed)!!
        val block = MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
        return ParseStep(index + 1, block)
    }

    private fun parseQuote(lines: List<String>, index: Int): ParseStep {
        val quote = StringBuilder()
        var nextLine = index
        while (nextLine < lines.size && lines[nextLine].trim().startsWith(">")) {
            val content = lines[nextLine].trim().removePrefix(">").trimStart()
            if (quote.isNotEmpty()) quote.append('\n')
            quote.append(content)
            nextLine++
        }
        return ParseStep(nextLine, MdBlock.Quote(quote.toString()))
    }

    private fun parseList(lines: List<String>, index: Int, firstLine: String): ParseStep {
        val ordered = ol.matches(firstLine)
        val items = ArrayList<MdListItem>()
        var nextLine = index
        while (nextLine < lines.size) {
            val trimmed = lines[nextLine].trim()
            val unorderedMatch = ul.matchEntire(trimmed)
            val orderedMatch = ol.matchEntire(trimmed)
            when {
                unorderedMatch != null && !ordered -> items.add(parseListItem(unorderedMatch.groupValues[2]))
                orderedMatch != null && ordered -> items.add(parseListItem(orderedMatch.groupValues[2]))
                trimmed.isEmpty() -> break
                else -> break
            }
            nextLine++
        }
        val block = if (items.isNotEmpty()) MdBlock.ListBlock(ordered, items) else null
        return ParseStep(nextLine, block)
    }

    private fun parseParagraph(lines: List<String>, index: Int, firstLine: String): ParseStep {
        val paragraph = StringBuilder(firstLine)
        var nextLine = index + 1
        while (nextLine < lines.size) {
            val trimmed = lines[nextLine].trim()
            if (isParagraphBoundary(trimmed)) break
            paragraph.append(' ').append(trimmed)
            nextLine++
        }
        return ParseStep(nextLine, MdBlock.Paragraph(paragraph.toString()))
    }

    private fun isParagraphBoundary(line: String): Boolean =
        line.isEmpty() || heading.matches(line) || ul.matches(line) || ol.matches(line) ||
            hr.matches(line) || fenceOpen.matches(line) || line.startsWith(">")

    private fun parseListItem(raw: String): MdListItem {
        val taskMatch = task.matchEntire(raw)
        return if (taskMatch != null) {
            MdListItem(
                text = taskMatch.groupValues[2],
                checked = taskMatch.groupValues[1].equals("x", ignoreCase = true),
            )
        } else {
            MdListItem(text = raw)
        }
    }
}
