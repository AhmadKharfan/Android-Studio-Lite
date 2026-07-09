package com.example.androidstudiolite.feature.folderpicker
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.designsystem.component.content.AslFileTreeNode
import com.example.androidstudiolite.domain.model.FolderNode
import com.example.androidstudiolite.domain.repository.FileSystemRepository
import com.example.androidstudiolite.feature.folderpicker.FolderPickerInteraction
import com.example.androidstudiolite.feature.folderpicker.FolderPickerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FolderPickerViewModel(
    private val fileSystemRepository: FileSystemRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderPickerUiState())
    val uiState: StateFlow<FolderPickerUiState> = _uiState.asStateFlow()

    private var rootItems: List<FolderNode> = emptyList()
    private var breadcrumbBase: List<String> = emptyList()

    init {
        viewModelScope.launch {
            val tree = fileSystemRepository.getFolderTree()
            rootItems = tree.items
            breadcrumbBase = tree.breadcrumb
            _uiState.update {
                it.copy(
                    breadcrumb = tree.breadcrumb,
                    items = tree.items.map(::toUiNode),
                    expandedIds = setOf("projects"),
                )
            }
        }
    }

    fun onInteraction(interaction: FolderPickerInteraction) {
        when (interaction) {
            is FolderPickerInteraction.ToggleFolder -> _uiState.update {
                val next = it.expandedIds.toMutableSet()
                if (!next.add(interaction.id)) next.remove(interaction.id)
                it.copy(expandedIds = next)
            }
            is FolderPickerInteraction.SelectFolder -> _uiState.update {
                it.copy(selectedId = interaction.id, selectedPath = buildPath(interaction.id))
            }
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
