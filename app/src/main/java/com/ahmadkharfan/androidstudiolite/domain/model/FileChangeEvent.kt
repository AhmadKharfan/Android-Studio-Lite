package com.ahmadkharfan.androidstudiolite.domain.model

/** The kind of mutation observed on the real filesystem. */
enum class FileChangeType {
    /** A file or directory was created. */
    CREATED,

    /** A file's contents were written/overwritten. */
    MODIFIED,

    /** A file or directory was deleted. */
    DELETED,

    /** A file or directory was renamed or moved; the event's `oldPath` holds the previous path. */
    MOVED,
}

/**
 * A single filesystem change observed by the local data layer. Emitted on the shared change bus by
 * every mutating repository operation so that observers (the editor's external-change detection, the
 * file tree's live refresh) can react without polling the disk.
 *
 * [path] is the absolute path of the affected entry; for [FileChangeType.MOVED] the entry now lives at
 * [path] and previously lived at [oldPath].
 */
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

/**
 * Thrown by [com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository.readText] when a
 * file exceeds the editor's in-memory size guard. Callers should surface a "file too large to open"
 * message rather than attempting to load it (which would risk an OOM).
 */
class FileTooLargeException(
    val path: String,
    val sizeBytes: Long,
    val limitBytes: Long,
) : Exception("File $path is $sizeBytes bytes, exceeding the ${limitBytes}-byte editor limit")
