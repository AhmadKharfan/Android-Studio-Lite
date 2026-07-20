package com.ahmadkharfan.androidstudiolite.feature.editor.engine.project

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionContext
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionPositionKind
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionProvider

class ProjectSymbolCompletionProvider(
    private val indexProvider: () -> ProjectSymbolIndex,
) : CompletionProvider {

    override fun complete(context: CompletionContext): List<CompletionItem> {
        val index = indexProvider()
        if (index.isEmpty()) return emptyList()
        return when (context.positionKind) {
            CompletionPositionKind.Import, CompletionPositionKind.MemberAccess ->
                context.qualifier?.let { index.membersOf(it) }.orEmpty()
            CompletionPositionKind.TypeReference -> index.typesMatching(context.prefix)
            CompletionPositionKind.NameReference -> index.topLevelMatching(context.prefix)
            CompletionPositionKind.CallArgument -> index.topLevelMatching(context.prefix)
            CompletionPositionKind.None -> emptyList()
        }
    }
}
