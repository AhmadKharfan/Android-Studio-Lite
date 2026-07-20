package com.ahmadkharfan.androidstudiolite.domain.model

sealed interface AgentAction {
    val tool: String

    data class ListDir(val path: String) : AgentAction {
        override val tool: String get() = "list_dir"
    }

    data class ReadFile(val path: String) : AgentAction {
        override val tool: String get() = "read_file"
    }

    data class Search(val query: String) : AgentAction {
        override val tool: String get() = "search"
    }

    data class CreateFile(val path: String, val content: String) : AgentAction {
        override val tool: String get() = "create_file"
    }

    data class CreateDir(val path: String) : AgentAction {
        override val tool: String get() = "create_dir"
    }

    data class EditFile(val path: String, val content: String) : AgentAction {
        override val tool: String get() = "edit_file"
    }

    data class Rename(val path: String, val newName: String) : AgentAction {
        override val tool: String get() = "rename"
    }

    data class Move(val path: String, val newParent: String) : AgentAction {
        override val tool: String get() = "move"
    }

    data class Delete(val path: String) : AgentAction {
        override val tool: String get() = "delete"
    }

    val mutating: Boolean
        get() = when (this) {
            is ListDir, is ReadFile, is Search -> false
            else -> true
        }
}

data class AgentToolResult(
    val action: AgentAction,
    val ok: Boolean,
    val output: String,
)
