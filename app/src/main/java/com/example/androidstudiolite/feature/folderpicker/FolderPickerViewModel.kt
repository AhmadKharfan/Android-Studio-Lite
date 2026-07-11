package com.example.androidstudiolite.feature.folderpicker
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.designsystem.component.content.AslFileTreeNode
import com.example.androidstudiolite.domain.model.FolderNode
import com.example.androidstudiolite.domain.repository.FileSystemRepository

class FolderPickerViewModel(
    private val fileSystemRepository: FileSystemRepository,
) : BaseViewModel<FolderPickerUiState, Nothing>(
    initialState = FolderPickerUiState(),
), FolderPickerInteractionListener {

    private var rootItems: List<FolderNode> = emptyList()
    private var breadcrumbBase: List<String> = emptyList()

    init {
        tryToExecute(
            block = { fileSystemRepository.getFolderTree() },
            onSuccess = { tree ->
                rootItems = tree.items
                breadcrumbBase = tree.breadcrumb
                updateState {
                    copy(
                        breadcrumb = tree.breadcrumb,
                        items = tree.items.map(::toUiNode),
                        expandedIds = setOf("projects"),
                    )
                }
            },
        )
    }

    override fun onToggleFolder(id: String) {
        updateState {
            val next = expandedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            copy(expandedIds = next)
        }
    }

    override fun onSelectFolder(id: String) {
        updateState {
            copy(selectedId = id, selectedPath = buildPath(id))
        }
    }

    private fun buildPath(id: String): String? {
        val chain = findChain(rootItems, id) ?: return null
        return (breadcrumbBase + chain.map { it.name }).joinToString(separator = "/", prefix = "/")
    }

    private fun findChain(nodes: List<FolderNode>, id: String, trail: List<FolderNode> = emptyList()): List<FolderNode>? {
        for (node in nodes) {
            val nextTrail = trail + node
            if (node.id == id) return nextTrail
            node.children?.let { children ->
                findChain(children, id, nextTrail)?.let { return it }
            }
        }
        return null
    }

    private fun toUiNode(node: FolderNode): AslFileTreeNode = AslFileTreeNode(
        id = node.id,
        name = node.name,
        children = node.children?.map(::toUiNode),
        icon = "folder",
    )
}
