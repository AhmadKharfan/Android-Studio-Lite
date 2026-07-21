package com.ahmadkharfan.androidstudiolite.feature.folderpicker
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTreeNode
import com.ahmadkharfan.androidstudiolite.domain.model.FolderNode
import com.ahmadkharfan.androidstudiolite.domain.repository.FileSystemRepository

class FolderPickerViewModel(
    private val fileSystemRepository: FileSystemRepository,
) : BaseViewModel<FolderPickerUiState, Nothing>(
    initialState = FolderPickerUiState(),
), FolderPickerInteractionListener {

    private var rootItems: List<FolderNode> = emptyList()
    private var breadcrumbBase: List<String> = emptyList()

    init {
        loadTree()
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
            copy(
                selectedId = id,
                selectedPath = buildPath(id),
                createFolderError = null,
            )
        }
    }

    override fun onStartCreateFolder() {
        updateState {
            copy(
                creatingFolder = true,
                newFolderName = "",
                createFolderError = null,
            )
        }
    }

    override fun onCancelCreateFolder() {
        updateState {
            copy(
                creatingFolder = false,
                newFolderName = "",
                createFolderError = null,
            )
        }
    }

    override fun onNewFolderNameChanged(name: String) {
        updateState {
            copy(newFolderName = name, createFolderError = null)
        }
    }

    override fun onConfirmCreateFolder() {
        val name = state.value.newFolderName.trim()
        if (name.isBlank()) {
            updateState { copy(createFolderError = "Enter a folder name") }
            return
        }
        if (name.contains('/') || name.contains('\\')) {
            updateState { copy(createFolderError = "Folder name cannot contain slashes") }
            return
        }
        val parentPath = state.value.selectedId ?: browseRootPath()
        tryToExecute(
            block = { fileSystemRepository.createDirectory(parentPath, name) },
            onSuccess = { newPath ->
                loadTree(
                    expandParentId = parentPath,
                    selectId = newPath,
                    creatingFolder = false,
                    newFolderName = "",
                )
            },
            onError = { error ->
                updateState {
                    copy(createFolderError = error.message ?: "Could not create folder")
                }
            },
        )
    }

    private fun loadTree(
        expandParentId: String? = null,
        selectId: String? = null,
        creatingFolder: Boolean = false,
        newFolderName: String = "",
    ) {
        tryToExecute(
            block = { fileSystemRepository.getFolderTree() },
            onSuccess = { tree ->
                rootItems = tree.items
                breadcrumbBase = tree.breadcrumb
                updateState {
                    copy(
                        breadcrumb = tree.breadcrumb,
                        items = tree.items.map(::toUiNode),
                        expandedIds = if (expandParentId != null) expandedIds + expandParentId else expandedIds,
                        selectedId = selectId ?: selectedId,
                        selectedPath = selectId?.let(::buildPath) ?: selectedPath,
                        creatingFolder = creatingFolder,
                        newFolderName = newFolderName,
                        createFolderError = null,
                    )
                }
            },
        )
    }

    private fun browseRootPath(): String =
        breadcrumbBase.joinToString(separator = "/", prefix = "/")

    private fun buildPath(id: String): String? {
        val chain = findChain(rootItems, id) ?: return id.takeIf { it.startsWith("/") }
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
