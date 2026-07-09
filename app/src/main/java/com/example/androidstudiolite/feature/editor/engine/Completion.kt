package com.example.androidstudiolite.feature.editor.engine
enum class CompletionKind { Keyword, Snippet, Function, Method, Property, Class, Variable, Parameter }
data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val detail: String? = null,
    val typeText: String? = null,
)
data class CompletionContext(
    val language: EditorLanguage,
    val text: String,
    val caret: Int,
    val prefix: String,
    val prefixStart: Int,
    val memberAccess: Boolean,
    val qualifier: String? = null,
    val importContext: Boolean = false,
    val composeContext: Boolean = false,
    val positionKind: CompletionPositionKind = CompletionPositionKind.NameReference,
    val suppressed: Boolean = false,
    val inTemplateExpression: Boolean = false,
    val callSite: CallSiteContext? = null,
)
data class CallSiteContext(
    val calleeName: String,
    val activeParameterIndex: Int,
    val suppliedNamedArgs: Set<String>,
    val editingNamedArgLabel: Boolean,
    val activeNamedArg: String?,
    val expectedType: String?,
)
fun interface CompletionProvider {
    fun complete(context: CompletionContext): List<CompletionItem>
}
object LspCompletionProvider : CompletionProvider {
    override fun complete(context: CompletionContext): List<CompletionItem> = emptyList()
}
object JavaLanguageServicePlaceholder : CompletionProvider {
    override fun complete(context: CompletionContext): List<CompletionItem> = emptyList()
}
