package com.ahmadkharfan.androidstudiolite.feature.editor.engine
data class SignatureHelpResult(
    val calleeName: String,
    val signatureLabel: String,
    val parameters: List<SignatureParameter>,
    val activeParameter: Int,
)
data class SignatureParameter(
    val label: String,
    val start: Int,
    val end: Int,
)
object KotlinSignatureHelpResolver {
    fun caretInsideCall(text: String, caret: Int): Boolean = enclosingCallParen(text, caret) >= 0

    fun enclosingCallParen(text: String, caret: Int): Int {
        var depth = 0
        var i = (caret - 1).coerceAtMost(text.length - 1)
        var guard = 0
        while (i >= 0 && guard < 4000) {
            when (text[i]) {
                ')', ']', '}' -> depth++
                '(' -> {
                    if (depth == 0) return i
                    depth--
                }
                '[', '{', ';' -> if (depth == 0) return -1
            }
            i--
            guard++
        }
        return -1
    }
    fun resolve(
        text: String,
        caret: Int,
        params: List<CallSignatureCatalog.Param>,
        calleeName: String,
        activeParameterIndex: Int,
    ): SignatureHelpResult? {
        if (params.isEmpty()) return null
        val sb = StringBuilder(calleeName.substringAfterLast('.')).append('(')
        val paramInfos = ArrayList<SignatureParameter>(params.size)
        params.forEachIndexed { index, param ->
            if (index > 0) sb.append(", ")
            val type = param.type ?: "Any"
            val part = "${param.name}: $type"
            val start = sb.length
            sb.append(part)
            paramInfos += SignatureParameter(part, start, sb.length)
        }
        sb.append(')')
        return SignatureHelpResult(
            calleeName = calleeName,
            signatureLabel = sb.toString(),
            parameters = paramInfos,
            activeParameter = activeParameterIndex.coerceIn(0, (params.size - 1).coerceAtLeast(0)),
        )
    }
    fun fromCallSite(text: String, caret: Int): SignatureHelpResult? {
        if (!caretInsideCall(text, caret)) return null
        val site = CallSiteScanner.enclosingCall(text, caret) ?: return null
        val params = CallSignatureCatalog.parametersFor(site.calleeName)
        if (params.isEmpty()) return null
        return resolve(
            text = text,
            caret = caret,
            params = params,
            calleeName = site.calleeName,
            activeParameterIndex = site.activeParameterIndex,
        )
    }
}
