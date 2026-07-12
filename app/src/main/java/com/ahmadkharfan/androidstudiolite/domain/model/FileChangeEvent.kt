package com.ahmadkharfan.androidstudiolite.domain.model

/** The kind of mutation observed on the real filesystem. */
enum class FileChangeType {
    /** A file or directory was created. */
    CREATED,

    /** A file's contents were written/overwritten. */
    MODIFIED,

    /** A file or directory was deleted. */
    DELETED,

    /** A file or directory was renamed or moved; [FileChangeEvent.oldPath] holds the previous path. */
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
data class FileChangeEvent(
    val type: FileChangeType,
    val path: String,
    val oldPath: String? = null,
)

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
