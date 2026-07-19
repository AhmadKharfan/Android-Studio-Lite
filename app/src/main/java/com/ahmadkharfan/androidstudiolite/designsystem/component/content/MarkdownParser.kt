package com.ahmadkharfan.androidstudiolite.designsystem.component.content

/** Lightweight block-level markdown model used by [AslMarkdownText]. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class ListBlock(val ordered: Boolean, val items: List<MdListItem>) : MdBlock
    data object HorizontalRule : MdBlock
}

data class MdListItem(val text: String, val checked: Boolean? = null)

/** Parses a subset of markdown into [MdBlock]s for Compose rendering. */
object MarkdownParser {

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
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> i++
                fenceOpen.matches(trimmed) -> {
                    val lang = fenceOpen.matchEntire(trimmed)!!.groupValues[1].ifBlank { "text" }
                    val body = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        if (body.isNotEmpty()) body.append('\n')
                        body.append(lines[i])
                        i++
                    }
                    if (i < lines.size) i++ // closing fence
                    blocks.add(MdBlock.Code(lang, body.toString()))
                }
                hr.matches(trimmed) -> {
                    blocks.add(MdBlock.HorizontalRule)
                    i++
                }
                heading.matches(trimmed) -> {
                    val m = heading.matchEntire(trimmed)!!
                    blocks.add(MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim()))
                    i++
                }
                trimmed.startsWith(">") -> {
                    val quote = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        val content = lines[i].trim().removePrefix(">").trimStart()
                        if (quote.isNotEmpty()) quote.append('\n')
                        quote.append(content)
                        i++
                    }
                    blocks.add(MdBlock.Quote(quote.toString()))
                }
                ul.matches(trimmed) || ol.matches(trimmed) -> {
                    val ordered = ol.matches(trimmed)
                    val items = ArrayList<MdListItem>()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        val ulMatch = ul.matchEntire(t)
                        val olMatch = ol.matchEntire(t)
                        when {
                            ulMatch != null && !ordered -> {
                                items.add(parseListItem(ulMatch.groupValues[2]))
                                i++
                            }
                            olMatch != null && ordered -> {
                                items.add(parseListItem(olMatch.groupValues[2]))
                                i++
                            }
                            t.isEmpty() -> break
                            else -> break
                        }
                    }
                    if (items.isNotEmpty()) blocks.add(MdBlock.ListBlock(ordered, items))
                }
                else -> {
                    val para = StringBuilder(trimmed)
                    i++
                    while (i < lines.size) {
                        val next = lines[i].trim()
                        if (next.isEmpty() || heading.matches(next) || ul.matches(next) || ol.matches(next) ||
                            hr.matches(next) || fenceOpen.matches(next) || next.startsWith(">")
                        ) {
                            break
                        }
                        para.append(' ').append(next)
                        i++
                    }
                    blocks.add(MdBlock.Paragraph(para.toString()))
                }
            }
        }
        return blocks
    }

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
