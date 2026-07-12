package com.ahmadkharfan.androidstudiolite.feature.editor.view
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslAutocompletePopup
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslSignatureHelpPopup
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslSuggestion
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslSuggestionKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLineGit
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.Diagnostic
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.LanguageDiagnostics
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
@Composable
fun AslEditableCodeEditor(
    session: EditorSession,
    fontSizeSp: Int,
    tabSize: Int,
    onEdited: () -> Unit,
    onCaretMoved: (line: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
    gitLineStatus: Map<Int, AslLineGit> = emptyMap(),
    breakpoints: Set<Int> = emptySet(),
    findQuery: String = "",
    findCurrentMatch: Int = 0,
    lspKotlin: Boolean = false,
    lspJava: Boolean = false,
    lspXml: Boolean = false,
    onDiagnostics: (List<Diagnostic>) -> Unit = {},
    revealNonce: Int = 0,
    revealOffset: Int = 0,
) {
    val colors = AslTheme.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.jetbrains_mono) }
    val palette = EditorPalette(
        canvas = colors.editorCanvas.toArgb(),
        gutter = colors.editorGutter.toArgb(),
        lineHighlight = colors.editorLineHighlight.toArgb(),
        selection = colors.editorSelection.toArgb(),
        cursor = colors.editorCursor.toArgb(),
        divider = colors.borderSubtle.toArgb(),
        gutterTextActive = colors.textSecondary.toArgb(),
        gutterTextInactive = colors.textTertiary.toArgb(),
        breakpoint = colors.error.toArgb(),
        defaultText = colors.syntaxVariable.toArgb(),
        keyword = colors.syntaxKeyword.toArgb(),
        string = colors.syntaxString.toArgb(),
        comment = colors.syntaxComment.toArgb(),
        function = colors.syntaxFunction.toArgb(),
        type = colors.syntaxType.toArgb(),
        variable = colors.syntaxVariable.toArgb(),
        number = colors.syntaxNumber.toArgb(),
        findMatch = colors.warning.copy(alpha = 0.25f).toArgb(),
        findCurrent = colors.accentPrimary.copy(alpha = 0.40f).toArgb(),
        bracketMatch = colors.accentPrimary.toArgb(),
        diagnosticError = colors.error.toArgb(),
        diagnosticWarning = colors.warning.toArgb(),
        diagnosticHint = colors.textTertiary.toArgb(),
    )
    val gitArgb = gitLineStatus.mapValues { (_, git) ->
        when (git) {
            AslLineGit.Added -> colors.success.toArgb()
            AslLineGit.Modified -> colors.info.toArgb()
            AslLineGit.Deleted -> colors.error.toArgb()
        }
    }
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    var overlay by remember { mutableStateOf<CompletionOverlay?>(null) }
    var signatureOverlay by remember { mutableStateOf<SignatureHelpOverlay?>(null) }
    var messageOverlay by remember { mutableStateOf<DiagnosticMessageOverlay?>(null) }
    var editorView by remember { mutableStateOf<CodeEditorView?>(null) }
    LaunchedEffect(revealNonce) {
        if (revealNonce > 0) editorView?.revealOffset(revealOffset)
    }
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        AndroidView(
            factory = { ctx ->
                CodeEditorView(ctx).also { view ->
                    view.onCompletionOverlay = { overlay = it }
                    view.onSignatureHelpOverlay = { signatureOverlay = it }
                    view.onDiagnosticMessage = { messageOverlay = it }
                    editorView = view
                }
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            update = { view ->
                view.analyzer = { s ->
                    val semantic = when (s.language) {
                        com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage.Kotlin -> lspKotlin
                        com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage.Java -> lspJava
                        com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage.Xml -> lspXml
                        com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage.Plain -> false
                    }
                    LanguageDiagnostics.analyze(s.text, s.language, semantic, s.filePath)
                }
                view.onDiagnostics = onDiagnostics
                view.bind(session, onEdited, onCaretMoved)
                view.updateOptions(
                    textSizePx = fontSizePx,
                    tabSize = tabSize,
                    palette = palette,
                    typeface = typeface,
                    densityScale = density.density,
                    gitColorByLine = gitArgb,
                    breakpointLines = breakpoints,
                    lspKotlin = lspKotlin,
                    lspJava = lspJava,
                    lspXml = lspXml,
                )
                view.setFind(findQuery, findCurrentMatch)
            },
        )
        val shown = overlay
        val rendered = remember { mutableStateOf<CompletionOverlay?>(null) }
        if (shown != null) rendered.value = shown
        val snapshot = rendered.value
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn(AslMotion.enterSpec()) + slideInVertically(AslMotion.enterSpec()) { it / 6 },
            exit = fadeOut(AslMotion.exitSpec()),
        ) {
            if (snapshot != null) {
                val popupWidthPx = with(density) { 320.dp.toPx() }
                val estHeightPx = with(density) { (minOf(snapshot.items.size, 8) * 34 + 8).dp.toPx() }
                val xPx = snapshot.anchorXpx.coerceIn(0f, (maxWidthPx - popupWidthPx).coerceAtLeast(0f))
                val yPx = snapshot.anchorYpx.coerceIn(0f, (maxHeightPx - estHeightPx).coerceAtLeast(0f))
                AslAutocompletePopup(
                    suggestions = snapshot.items.map { it.toSuggestion() },
                    activeIndex = snapshot.selectedIndex,
                    onSelect = { _, index -> editorView?.acceptCompletionAt(index) },
                    modifier = Modifier.offset(
                        x = with(density) { xPx.toDp() },
                        y = with(density) { yPx.toDp() },
                    ),
                )
            }
        }
        val sigShown = signatureOverlay
        val showSignatureHelp = sigShown != null && shown == null
        AnimatedVisibility(
            visible = showSignatureHelp,
            enter = fadeIn(AslMotion.enterSpec()) + slideInVertically(AslMotion.enterSpec()) { -it / 8 },
            exit = fadeOut(AslMotion.exitSpec()),
        ) {
            if (sigShown != null) {
                val popupWidthPx = with(density) { 360.dp.toPx() }
                val estHeightPx = with(density) { 56.dp.toPx() }
                val xPx = sigShown.anchorXpx.coerceIn(0f, (maxWidthPx - popupWidthPx).coerceAtLeast(0f))
                val yPx = (sigShown.anchorYpx - estHeightPx - with(density) { 8.dp.toPx() })
                    .coerceIn(0f, (maxHeightPx - estHeightPx).coerceAtLeast(0f))
                AslSignatureHelpPopup(
                    help = sigShown.help,
                    modifier = Modifier.offset(
                        x = with(density) { xPx.toDp() },
                        y = with(density) { yPx.toDp() },
                    ),
                )
            }
        }
        val msg = messageOverlay
        if (msg != null) {
            val popupWidthDp = 300.dp
            val popupWidthPx = with(density) { popupWidthDp.toPx() }
            val estHeightPx = with(density) { 120.dp.toPx() }
            val xPx = msg.anchorXpx.coerceIn(0f, (maxWidthPx - popupWidthPx).coerceAtLeast(0f))
            val yPx = (msg.anchorYpx + with(density) { 12.dp.toPx() })
                .coerceIn(0f, (maxHeightPx - estHeightPx).coerceAtLeast(0f))
            val tone = when (msg.severity) {
                com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Error -> colors.error
                com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Warning -> if (msg.muted) colors.textTertiary else colors.warning
                else -> colors.info
            }
            androidx.compose.material3.Surface(
                color = colors.bgElevated,
                contentColor = colors.textPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.5f)),
                modifier = Modifier
                    .width(popupWidthDp)
                    .heightIn(max = 200.dp)
                    .offset(x = with(density) { xPx.toDp() }, y = with(density) { yPx.toDp() }),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = msg.message,
                        style = AslCode.codeSmall,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
private fun CompletionItem.toSuggestion(): AslSuggestion = AslSuggestion(
    label = label,
    detail = detail,
    type = typeText,
    kind = when (kind) {
        CompletionKind.Keyword -> AslSuggestionKind.Keyword
        CompletionKind.Snippet -> AslSuggestionKind.Snippet
        CompletionKind.Function, CompletionKind.Method -> AslSuggestionKind.Method
        CompletionKind.Class -> AslSuggestionKind.Class
        CompletionKind.Property, CompletionKind.Variable, CompletionKind.Parameter -> AslSuggestionKind.Field
    },
)
