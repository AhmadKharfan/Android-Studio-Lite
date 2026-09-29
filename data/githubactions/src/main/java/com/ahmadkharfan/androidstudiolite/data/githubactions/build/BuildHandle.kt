package com.ahmadkharfan.androidstudiolite.data.githubactions.build

/**
 * What the app persists to find a GitHub Actions build again: the build repository, the id the app
 * gave the dispatch, and the run id once GitHub has reported it.
 *
 * Encoded as `v1|owner|repo|correlationId|runId` (runId empty until known). A build started before its
 * run id was known can still be re-attached, by looking the run up through its correlation id.
 */
internal data class BuildHandle(
    val owner: String,
    val repo: String,
    val correlationId: String,
    val runId: Long? = null,
) {
    fun encode(): String = listOf(VERSION, owner, repo, correlationId, runId?.toString().orEmpty()).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "v1"
        private const val SEPARATOR = "|"
        private const val FIELDS = 5

        fun decode(value: String): BuildHandle? {
            val parts = value.split(SEPARATOR)
            if (parts.size != FIELDS || parts[0] != VERSION) return null
            if (parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) return null
            val runId = parts[4].takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: return null }
            return BuildHandle(parts[1], parts[2], parts[3], runId)
        }
    }
}
