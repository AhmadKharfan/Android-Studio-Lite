package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitDetails
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitFileChange
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitIdentity
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitSummary
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitLogPage
import com.ahmadkharfan.androidstudiolite.domain.model.GitRefKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitRefLabel
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.FollowFilter
import org.eclipse.jgit.revwalk.RenameCallback
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.ByteArrayOutputStream

/** Read-only history operations kept separate from the mutating repository command surface. */
internal class JGitHistoryEngine {

    fun log(repo: Repository, cursor: String?, limit: Int): GitLogPage {
        val head = repo.resolve(Constants.HEAD) ?: return GitLogPage(emptyList(), null)
        val refs = decorations(repo)
        val shallow = shallowIds(repo)
        RevWalk(repo).use { walk ->
            walk.sort(RevSort.TOPO, true)
            walk.sort(RevSort.COMMIT_TIME_DESC, true)
            walk.markStart(walk.parseCommit(head))
            return page(walk.asSequence(), cursor, limit) { commit ->
                commit.toSummary(repo, refs[commit.name].orEmpty(), shallow)
            }
        }
    }

    fun fileHistory(repo: Repository, path: String, cursor: String?, limit: Int): GitLogPage {
        val head = repo.resolve(Constants.HEAD) ?: return GitLogPage(emptyList(), null)
        val refs = decorations(repo)
        val shallow = shallowIds(repo)
        val renames = mutableMapOf<String, DiffEntry>()
        val filter = FollowFilter.create(path, repo.config.get(org.eclipse.jgit.diff.DiffConfig.KEY)).apply {
            renameCallback = object : RenameCallback() {
                override fun renamed(entry: DiffEntry) = Unit
                override fun renamed(entry: DiffEntry, commit: RevCommit) {
                    renames[commit.name] = entry
                }
            }
        }
        RevWalk(repo).use { walk ->
            walk.sort(RevSort.TOPO, true)
            walk.sort(RevSort.COMMIT_TIME_DESC, true)
            walk.treeFilter = filter
            walk.markStart(walk.parseCommit(head))
            var historicalPath = path
            val summaries = walk.asSequence().map { commit ->
                val rename = renames[commit.name]
                val commitPath = rename?.newPath ?: historicalPath
                commit.toSummary(repo, refs[commit.name].orEmpty(), shallow, commitPath).also {
                    if (rename != null) historicalPath = rename.oldPath
                }
            }
            return page(summaries, cursor, limit) { it }
        }
    }

    fun commitDetails(repo: Repository, commitId: String): GitCommitDetails {
        val refs = decorations(repo)
        val shallow = shallowIds(repo)
        RevWalk(repo).use { walk ->
            val commit = walk.parseCommit(
                repo.resolve(commitId) ?: throw GitException.Unknown("Unknown commit: $commitId"),
            )
            val oldTree = commit.parents.firstOrNull()?.let { walk.parseCommit(it).tree }
            val entries = DiffFormatter(ByteArrayOutputStream()).use { formatter ->
                formatter.setRepository(repo)
                val oldIterator = oldTree?.let { CanonicalTreeParser(null, repo.newObjectReader(), it) }
                    ?: EmptyTreeIterator()
                val newIterator = CanonicalTreeParser(null, repo.newObjectReader(), commit.tree)
                formatter.scan(oldIterator, newIterator)
            }.let { entries ->
                if (entries.size <= RENAME_LIMIT) RenameDetector(repo).apply { addAll(entries) }.compute() else entries
            }
            return GitCommitDetails(
                id = commit.name,
                shortId = commit.abbreviate(SHORT_ID_LENGTH).name(),
                fullMessage = commit.fullMessage,
                author = commit.authorIdent.toIdentity(),
                committer = commit.committerIdent.toIdentity(),
                parents = commit.parents.map { it.name },
                refs = refs[commit.name].orEmpty(),
                changedFiles = entries.map { entry ->
                    GitCommitFileChange(
                        path = entry.newPath.takeUnless { it == DiffEntry.DEV_NULL } ?: entry.oldPath,
                        oldPath = entry.oldPath.takeIf {
                            entry.changeType in setOf(DiffEntry.ChangeType.RENAME, DiffEntry.ChangeType.COPY)
                        },
                        type = entry.changeType.toDomainType(),
                    )
                },
                isShallowBoundary = commit.isBoundary(repo, shallow),
            )
        }
    }

    fun blame(git: Git, path: String): List<GitBlameLine> {
        val repo = git.repository
        val headTree = repo.resolve("${Constants.HEAD}^{tree}") ?: return emptyList()
        val bytes = TreeWalk.forPath(repo, path, headTree)?.use { walk ->
            repo.open(walk.getObjectId(0), Constants.OBJ_BLOB).let { loader ->
                if (loader.size > MAX_BYTES) throw GitException.TooLarge("Blame is limited to files under 512 KiB")
                loader.bytes
            }
        } ?: return emptyList()
        val raw = RawText(bytes)
        if (raw.size() > MAX_LINES) throw GitException.TooLarge("Blame is limited to files under 20,000 lines")
        val result = git.blame().setFilePath(path).setFollowFileRenames(true).call() ?: return emptyList()
        result.computeAll()
        val contents = result.resultContents ?: return emptyList()
        return (0 until contents.size()).map { index ->
            val commit = result.getSourceCommit(index)
            val author = result.getSourceAuthor(index)
            GitBlameLine(
                lineNo = index + 1,
                commitId = commit?.name.orEmpty(),
                shortId = commit?.abbreviate(SHORT_ID_LENGTH)?.name().orEmpty(),
                authorName = author?.name.orEmpty(),
                authorTimeMillis = author?.whenAsInstant?.toEpochMilli() ?: 0L,
                lineText = contents.getString(index),
            )
        }
    }

    fun listTags(repo: Repository): List<GitTag> = RevWalk(repo).use { walk ->
        repo.refDatabase.getRefsByPrefix(Constants.R_TAGS).mapNotNull { ref ->
            val peeled = repo.refDatabase.peel(ref)
            val target = peeled.peeledObjectId ?: peeled.objectId ?: return@mapNotNull null
            val parsed = runCatching { walk.parseAny(ref.objectId) }.getOrNull()
            val tag = parsed as? RevTag
            GitTag(
                name = Repository.shortenRefName(ref.name),
                target = target.name,
                annotated = tag != null,
                message = tag?.fullMessage,
            )
        }.sortedBy { it.name }
    }

    private fun decorations(repo: Repository): Map<String, List<GitRefLabel>> {
        val labels = mutableMapOf<String, MutableList<GitRefLabel>>()
        fun add(id: String?, label: GitRefLabel) {
            if (id != null) labels.getOrPut(id) { mutableListOf() } += label
        }
        repo.refDatabase.refs.forEach { ref ->
            val peeled = repo.refDatabase.peel(ref)
            val id = peeled.peeledObjectId ?: peeled.objectId
            ref.toLabel()?.let { add(id?.name, it) }
        }
        repo.exactRef(Constants.HEAD)?.objectId?.name?.let { add(it, GitRefLabel("HEAD", GitRefKind.HEAD)) }
        return labels.mapValues { (_, value) -> value.distinct().sortedBy { it.kind.ordinal } }
    }

    private fun Ref.toLabel(): GitRefLabel? = when {
        name.startsWith(Constants.R_HEADS) -> GitRefLabel(Repository.shortenRefName(name), GitRefKind.BRANCH)
        name.startsWith(Constants.R_REMOTES) -> GitRefLabel(Repository.shortenRefName(name), GitRefKind.REMOTE)
        name.startsWith(Constants.R_TAGS) -> GitRefLabel(Repository.shortenRefName(name), GitRefKind.TAG)
        else -> null
    }

    private fun shallowIds(repo: Repository): Set<String> =
        runCatching { repo.objectDatabase.shallowCommits.mapTo(mutableSetOf()) { it.name } }.getOrDefault(emptySet())

    private fun RevCommit.toSummary(
        repo: Repository,
        refs: List<GitRefLabel>,
        shallow: Set<String>,
        path: String? = null,
    ) = GitCommitSummary(
        id = name,
        shortId = abbreviate(SHORT_ID_LENGTH).name(),
        message = shortMessage,
        fullMessage = fullMessage,
        authorName = authorIdent.name,
        authorEmail = authorIdent.emailAddress,
        authorTimeMillis = authorIdent.whenAsInstant.toEpochMilli(),
        parents = parents.map { it.name },
        refs = refs,
        isShallowBoundary = isBoundary(repo, shallow),
        path = path,
    )

    private fun RevCommit.isBoundary(repo: Repository, shallow: Set<String>): Boolean =
        name in shallow || parents.any { parent -> runCatching { repo.open(parent, Constants.OBJ_COMMIT) }.isFailure }

    private fun PersonIdent.toIdentity() = GitCommitIdentity(name, emailAddress, whenAsInstant.toEpochMilli())

    private fun DiffEntry.ChangeType.toDomainType() = when (this) {
        DiffEntry.ChangeType.ADD -> GitCommitChangeType.ADDED
        DiffEntry.ChangeType.MODIFY -> GitCommitChangeType.MODIFIED
        DiffEntry.ChangeType.DELETE -> GitCommitChangeType.DELETED
        DiffEntry.ChangeType.RENAME -> GitCommitChangeType.RENAMED
        DiffEntry.ChangeType.COPY -> GitCommitChangeType.COPIED
    }

    private fun <T, R> page(
        values: Sequence<T>,
        cursor: String?,
        limit: Int,
        transform: (T) -> R,
    ): GitLogPage where R : Any {
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        var afterCursor = cursor == null
        val selected = mutableListOf<R>()
        var hasMore = false
        for (value in values) {
            val transformed = transform(value)
            val id = when (transformed) {
                is GitCommitSummary -> transformed.id
                else -> error("History pages require commit summaries")
            }
            if (!afterCursor) {
                if (id == cursor) afterCursor = true
                continue
            }
            if (selected.size == safeLimit) {
                hasMore = true
                break
            }
            selected += transformed
        }
        @Suppress("UNCHECKED_CAST")
        val commits = selected as List<GitCommitSummary>
        return GitLogPage(commits, if (hasMore) commits.lastOrNull()?.id else null)
    }

    private companion object {
        const val SHORT_ID_LENGTH = 7
        const val MAX_BYTES = 512 * 1024
        const val MAX_LINES = 20_000
        const val MAX_PAGE_SIZE = 200
        const val RENAME_LIMIT = 2_000
    }
}
