package com.ahmadkharfan.androidstudiolite.feature.editor.engine.project

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionContext
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionPositionKind
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionProvider

/**
 * Contributes project + dependency symbols (from the last sync's [ProjectSymbolIndex]) alongside the
 * built-in stdlib/Android catalog. Reads the index through a provider so the controller can swap in a
 * fresh index after each sync without rebuilding anything.
 *
 * The index it reads is empty until a project syncs, in which case this provider is a no-op and the
 * built-in catalog behaves exactly as before.
 */
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
