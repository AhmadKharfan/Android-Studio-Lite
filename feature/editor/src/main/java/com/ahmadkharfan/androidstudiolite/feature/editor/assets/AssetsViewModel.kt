package com.ahmadkharfan.androidstudiolite.feature.editor.assets

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AssetsViewModel(
    private val projectId: String,
    private val projectPathResolver: ProjectPathResolver,
) : BaseViewModel<AssetsUiState, Nothing>(
    initialState = AssetsUiState(),
), AssetsInteractionListener {

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

    override fun onSelectAsset(asset: AssetEntry) {
        updateState { copy(selectedAsset = asset) }
    }

    override fun onDismissAssetDetail() {
        updateState { copy(selectedAsset = null) }
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
                    .mapNotNull { file ->
                        val folder = file.parentFile?.name ?: resDir.name
                        if (shouldSkipFolder(folder)) return@mapNotNull null
                        AssetEntry(
                            name = file.name,
                            subtitle = folder,
                            absolutePath = file.absolutePath,
                            kind = classify(file, folder),
                        )
                    }
            }
            .distinctBy { it.absolutePath }
            .sortedWith(compareBy({ it.subtitle }, { it.name }))
    }

    private fun shouldSkipFolder(folder: String): Boolean {
        val folderLower = folder.lowercase()
        return folderLower == "layout" || folderLower.startsWith("values")
    }

    private fun classify(file: File, folder: String): AssetKind {
        val ext = file.extension.lowercase()
        val folderLower = folder.lowercase()
        return when {
            ext in RASTER_EXTENSIONS -> AssetKind.RasterImage
            (folderLower.startsWith("drawable") || folderLower.startsWith("mipmap")) && ext == "xml" ->
                AssetKind.XmlDrawable
            folderLower == "font" || ext in FONT_EXTENSIONS -> AssetKind.Font
            else -> AssetKind.Raw
        }
    }

    private companion object {
        val RASTER_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "gif")
        val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    }
}
