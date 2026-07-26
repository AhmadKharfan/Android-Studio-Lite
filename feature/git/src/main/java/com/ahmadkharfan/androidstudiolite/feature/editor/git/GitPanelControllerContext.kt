package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class GitPanelControllerContext(
    private val scope: CoroutineScope,
    val repoDir: () -> File?,
    val state: () -> GitPanelUiState,
    val updateState: (GitPanelUiState.() -> GitPanelUiState) -> Unit,
) {
    fun <R> execute(
        block: suspend () -> R,
        onSuccess: (R) -> Unit = {},
        onError: (Throwable) -> Unit = ::showError,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                onSuccess(block())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                onError(error)
            }
        }
    }

    fun showError(error: Throwable) {
        updateState { copy(statusMessage = gitErrorMessage(error)) }
    }
}
