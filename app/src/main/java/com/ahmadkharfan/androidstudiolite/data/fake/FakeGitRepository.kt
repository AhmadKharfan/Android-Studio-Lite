package com.ahmadkharfan.androidstudiolite.data.fake

import com.ahmadkharfan.androidstudiolite.domain.model.GitChange
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val DIFFS = mapOf(
    "MainActivity.kt" to listOf(
        GitDiffLine(GitDiffKind.CONTEXT, "class MainActivity : ComponentActivity() {", oldNo = 2, newNo = 2),
        GitDiffLine(GitDiffKind.CONTEXT, "  override fun onCreate(state: Bundle?) {", oldNo = 3, newNo = 3),
        GitDiffLine(GitDiffKind.ADDED, "    super.onCreate(state)", newNo = 4),
        GitDiffLine(GitDiffKind.ADDED, "    val greeting = \"Hello, Lite!\"", newNo = 5),
        GitDiffLine(GitDiffKind.ADDED, "    setContent", newNo = 6),
        GitDiffLine(GitDiffKind.CONTEXT, "  }", oldNo = 4, newNo = 7),
        GitDiffLine(GitDiffKind.CONTEXT, "}", oldNo = 5, newNo = 8),
    ),
    "MainViewModel.kt" to listOf(
        GitDiffLine(GitDiffKind.ADDED, "package com.ahmadkharfan.androidstudiolite", newNo = 1),
        GitDiffLine(GitDiffKind.ADDED, "", newNo = 2),
        GitDiffLine(GitDiffKind.ADDED, "class MainViewModel", newNo = 3),
    ),
    "build.gradle.kts" to listOf(
        GitDiffLine(GitDiffKind.CONTEXT, "dependencies {", oldNo = 12, newNo = 12),
        GitDiffLine(GitDiffKind.REMOVED, "    implementation(\"androidx.core:core-ktx:1.12.0\")", oldNo = 13),
        GitDiffLine(GitDiffKind.ADDED, "    implementation(\"androidx.core:core-ktx:1.13.1\")", newNo = 13),
        GitDiffLine(GitDiffKind.CONTEXT, "}", oldNo = 14, newNo = 14),
    ),
    "local.properties" to listOf(
        GitDiffLine(GitDiffKind.ADDED, "sdk.dir=/home/user/Android/Sdk", newNo = 1),
    ),
)

class FakeGitRepository : GitRepository {

    private val state = MutableStateFlow(
        GitState(
            branch = "main",
            changes = listOf(
                GitChange("MainActivity.kt", GitFileStatus.MODIFIED),
                GitChange("MainViewModel.kt", GitFileStatus.ADDED),
                GitChange("build.gradle.kts", GitFileStatus.MODIFIED),
                GitChange("local.properties", GitFileStatus.UNTRACKED),
            ),
        ),
    )

    override fun observeState(): StateFlow<GitState> = state

    override suspend fun getDiff(path: String): List<GitDiffLine> = DIFFS[path].orEmpty()

    override suspend fun setCommitMessage(message: String) {
        state.value = state.value.copy(commitMessage = message)
    }

    override suspend fun commit() {
        state.value = state.value.copy(committing = true)
        delay(900)
        state.value = state.value.copy(changes = emptyList(), commitMessage = "", committing = false)
    }
}
