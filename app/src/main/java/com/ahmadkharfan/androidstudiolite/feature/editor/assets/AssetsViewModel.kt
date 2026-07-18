package com.ahmadkharfan.androidstudiolite.feature.editor.assets

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lists the project's real resources and raw assets straight from disk (no Gradle, no build). It walks
 * every module's `src/main/res` and `src/main/assets`, so the panel reflects what's actually on the
 * filesystem rather than a fixed sample list.
 */
class AssetsViewModel(
    private val projectId: String,
    private val projectPathResolver: ProjectPathResolver,
) : BaseViewModel<AssetsUiState, Nothing>(
    initialState = AssetsUiState(),
) {

    init {
        tryToExecute(
            block = {
                val root = projectPathResolver(projectId)
                withContext(Dispatchers.IO) { scanAssets(root) }
            },
            onSuccess = { assets -> updateState { copy(loading = false, assets = assets) } },
            onError = { updateState { copy(loading = false, assets = emptyList()) } },
        )
    }

    private fun scanAssets(root: File): List<AssetEntry> {
        val resourceRoots = root.walkTopDown()
            .onEnter { it.name != ".git" && it.name != "build" && it.name != ".gradle" }
            .filter { it.isDirectory && (it.name == "res" || it.name == "assets") }
            .filter { it.parentFile?.name == "main" || it.parentFile?.parentFile?.name == "src" }
            .toList()

        return resourceRoots
            .flatMap { resDir ->
                resDir.walkTopDown()
                    .filter { it.isFile }
                    .map { file ->
                        AssetEntry(
                            name = file.name,
                            subtitle = file.parentFile?.name ?: resDir.name,
                            icon = iconFor(file.extension.lowercase()),
                            absolutePath = file.absolutePath,
                        )
                    }
            }
            .distinctBy { it.absolutePath }
            .sortedWith(compareBy({ it.subtitle }, { it.name }))
    }

    private fun iconFor(extension: String): String = when (extension) {
        "png", "webp", "jpg", "jpeg", "gif" -> "image"
        "svg", "xml" -> "shapes"
        "ttf", "otf" -> "type"
        "json" -> "braces"
        else -> "file"
    }
}
