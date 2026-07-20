package com.ahmadkharfan.androidstudiolite.domain.model

enum class FileChangeType {
    CREATED,

    MODIFIED,

    DELETED,

    MOVED,
}

sealed class FileChangeEvent {
    abstract val type: FileChangeType?
    abstract val path: String
    abstract val oldPath: String?

    data class PathChanged(
        override val type: FileChangeType,
        override val path: String,
        override val oldPath: String? = null,
    ) : FileChangeEvent()

    data class RootInvalidated(
        val root: String,
        val generation: Long,
        val reason: RootInvalidationReason,
    ) : FileChangeEvent() {
        override val type: FileChangeType? = null
        override val path: String = root
        override val oldPath: String? = null
    }
}

enum class RootInvalidationReason {
    GIT_OPERATION,
    EXTERNAL,
}

class FileTooLargeException(
    val path: String,
    val sizeBytes: Long,
    val limitBytes: Long,
) : Exception("File $path is $sizeBytes bytes, exceeding the ${limitBytes}-byte editor limit")
