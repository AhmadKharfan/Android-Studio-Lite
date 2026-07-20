package com.ahmadkharfan.androidstudiolite.feature.editor.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ahmadkharfan.androidstudiolite.designsystem.editor.EditorPalette
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslAutocompletePopup
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslSuggestion
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslSuggestionKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLineGit
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession

@Composable
fun AslEditableCodeEditor(
    session: EditorSession,
    fontSizeSp: Int,
    tabSize: Int,
    onEdited: () -> Unit,
    onCaretMoved: (line: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
    colorSchemeId: String = "darcula",
    fontFamilyId: String = "jetbrains",
    gitLineStatus: Map<Int, AslLineGit> = emptyMap(),
    breakpoints: Set<Int> = emptySet(),
    findQuery: String = "",
    findCurrentMatch: Int = 0,
    revealNonce: Int = 0,
    revealOffset: Int = 0,
    enableVolumeKeys: Boolean = true,
    projectIndex: com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex =
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex.EMPTY,
) {
    val colors = AslTheme.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val typeface = remember(fontFamilyId) {
        when (fontFamilyId) {
            "monospace" -> android.graphics.Typeface.MONOSPACE
            else -> ResourcesCompat.getFont(
                context,
                com.ahmadkharfan.androidstudiolite.designsystem.R.font.jetbrains_mono,
            )
        }
    }
    val palette = remember(colorSchemeId) { EditorPalette.forScheme(colorSchemeId) }
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
    var editorView by remember { mutableStateOf<CodeEditorView?>(null) }
    LaunchedEffect(revealNonce) {
        if (revealNonce > 0) editorView?.revealOffset(revealOffset)
    }
    EditorVolumeScrollEffect(enabled = enableVolumeKeys) { volumeUp ->
        editorView?.moveCaretByVolume(volumeUp)
    }
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        AndroidView(
            factory = { ctx ->
                CodeEditorView(ctx).also { view ->
                    view.onCompletionOverlay = { overlay = it }
                    view.onSignatureHelpOverlay = { signatureOverlay = it }
                    editorView = view
                }
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            update = { view ->
                view.setProjectIndex(projectIndex)
                view.bind(session, onEdited, onCaretMoved)
                view.updateOptions(
                    textSizePx = fontSizePx,
                    tabSize = tabSize,
                    palette = palette,
                    typeface = typeface,
                    densityScale = density.density,
                    gitColorByLine = gitArgb,
                    breakpointLines = breakpoints,
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
    }
}

@Composable
fun EditorVolumeScrollEffect(
    enabled: Boolean,
    onVolumeKey: (volumeUp: Boolean) -> Unit,
) {
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        EditorVolumeKeyDispatcher.handler = { event ->
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    onVolumeKey(false)
                    true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    onVolumeKey(true)
                    true
                }
                else -> false
            }
        }
        onDispose {
            if (EditorVolumeKeyDispatcher.handler != null) {
                EditorVolumeKeyDispatcher.handler = null
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
