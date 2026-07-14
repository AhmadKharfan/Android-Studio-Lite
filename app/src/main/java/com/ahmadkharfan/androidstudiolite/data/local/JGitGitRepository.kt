package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitChange
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Real [GitRepository] over [JGit](https://www.eclipse.org/jgit/). All calls run on [io] and never
 * touch the main thread. Per-repository [GitState] is cached in [states] and refreshed after any
 * mutation so the git panel stays in sync.
 */
class JGitGitRepository(
    private val projectsHome: () -> File,
    private val credentialStore: GitCredentialStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : GitRepository {

    private val states = ConcurrentHashMap<String, MutableStateFlow<GitState>>()
    private val commitMessages = ConcurrentHashMap<String, String>()

    // region clone

    override fun clone(url: String, branch: String?, credentials: GitCredentials?): Flow<CloneProgress> =
        callbackFlow {
            val cleanUrl = url.trim()
            hostOf(cleanUrl)?.let { host ->
                credentials?.takeIf { it.token.isNotBlank() }?.let { credentialStore.save(host, it) }
            }
            val destination = uniqueDestination(deriveRepoName(cleanUrl))
            val monitor = object : EmptyProgressMonitor() {
                private var task = ""
                private var total = 0
                private var done = 0
                override fun beginTask(title: String?, totalWork: Int) {
                    task = title.orEmpty()
                    total = totalWork
                    done = 0
                    emit(0)
                }
                override fun update(completed: Int) {
                    done += completed
                    emit(done)
                }
                private fun emit(current: Int) {
                    val fraction = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else null
                    trySendBlocking(CloneProgress(fraction = fraction, message = task.ifBlank { "Cloning" }))
                }
            }
            trySendBlocking(CloneProgress(fraction = 0f, message = "Resolving $cleanUrl"))
            try {
                Git.cloneRepository()
                    .setURI(cleanUrl)
                    .setDirectory(destination)
                    .apply { branch?.takeIf { it.isNotBlank() }?.let { setBranch(it) } }
                    .setCredentialsProvider(credentialProviderFor(cleanUrl, credentials))
                    .setProgressMonitor(monitor)
                    .call()
                    .close()
                trySendBlocking(
                    CloneProgress(fraction = 1f, message = "Done", clonedProjectId = destination.name),
                )
                close()
            } catch (t: Throwable) {
                // A partially-cloned directory is useless; don't leave it behind as a phantom project.
                destination.deleteRecursively()
                close(t)
            }
            awaitClose { }
        }.flowOn(io)

    // endregion

    override fun observeState(repoDir: File): StateFlow<GitState> = flowForOrCompute(repoDir)

    override suspend fun refresh(repoDir: File) = withContext(io) {
        flowForOrCompute(repoDir).value = computeState(repoDir)
    }

    override suspend fun getDiff(repoDir: File, path: String): List<GitDiffLine> = withContext(io) {
        if (!isRepo(repoDir)) return@withContext emptyList()
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val out = ByteArrayOutputStream()
            DiffFormatter(out).use { formatter ->
                formatter.setRepository(repo)
                formatter.pathFilter = PathFilter.create(path)
                formatter.format(headTreeIterator(repo), FileTreeIterator(repo))
            }
            parseUnifiedDiff(out.toString(Charsets.UTF_8.name()))
        }
    }

    override suspend fun stage(repoDir: File, path: String) = mutate(repoDir) { git ->
        // addFilepattern stages new/modified content; a deletion needs an explicit rm from the index.
        if (File(repoDir, path).exists()) {
            git.add().addFilepattern(path).call()
        } else {
            git.rm().addFilepattern(path).setCached(true).call()
        }
    }

    override suspend fun unstage(repoDir: File, path: String) = mutate(repoDir) { git ->
        git.reset().addPath(path).call()
    }

    override suspend fun setCommitMessage(repoDir: File, message: String) = withContext(io) {
        commitMessages[key(repoDir)] = message
        val flow = states[key(repoDir)] ?: return@withContext
        flow.value = flow.value.copy(commitMessage = message)
    }

    override suspend fun commit(repoDir: File): String = withContext(io) {
        val id = Git.open(repoDir).use { git ->
            val status = git.status().call()
            val hasStaged = status.added.isNotEmpty() || status.changed.isNotEmpty() ||
                status.removed.isNotEmpty()
            // Match `git commit -a` ergonomics: if nothing is staged, fold in tracked modifications.
            if (!hasStaged) git.add().addFilepattern(".").setUpdate(true).call()
            val (name, email) = identityFor(git.repository)
            git.commit()
                .setMessage(commitMessages[key(repoDir)].orEmpty())
                .setAuthor(name, email)
                .setCommitter(name, email)
                .call()
                .name
        }
        commitMessages.remove(key(repoDir))
        flowForOrCompute(repoDir).value = computeState(repoDir).copy(commitMessage = "")
        id
    }

    override suspend fun branches(repoDir: File): List<GitBranch> = withContext(io) {
        if (!isRepo(repoDir)) return@withContext emptyList()
        Git.open(repoDir).use { git ->
            val current = git.repository.branch
            git.branchList().call().map { ref ->
                val shortName = Repository.shortenRefName(ref.name)
                GitBranch(
                    name = shortName,
                    isRemote = ref.name.startsWith(Constants.R_REMOTES),
                    current = shortName == current,
                )
            }
        }
    }

    override suspend fun createBranch(repoDir: File, name: String, checkout: Boolean) =
        mutate(repoDir) { git ->
            git.branchCreate().setName(name).call()
            if (checkout) git.checkout().setName(name).call()
        }

    override suspend fun checkout(repoDir: File, name: String) = mutate(repoDir) { git ->
        git.checkout().setName(name).call()
    }

    override suspend fun log(repoDir: File, max: Int): List<GitCommit> = withContext(io) {
        if (!isRepo(repoDir)) return@withContext emptyList()
        Git.open(repoDir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) return@withContext emptyList()
            git.log().setMaxCount(max).call().map { it.toGitCommit() }
        }
    }

    override suspend fun push(repoDir: File): GitSyncResult = withContext(io) {
        runCatching {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                val branch = repo.branch
                val remote = BranchConfig(repo.config, branch).remote ?: Constants.DEFAULT_REMOTE_NAME
                val results = git.push()
                    .setRemote(remote)
                    .setRefSpecs(RefSpec("${Constants.R_HEADS}$branch:${Constants.R_HEADS}$branch"))
                    .setCredentialsProvider(credentialProviderFor(remoteUrl(repo, remote), null))
                    .call()
                val updates = results.flatMap { it.remoteUpdates }
                val rejected = updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }
                if (rejected == null) {
                    GitSyncResult(true, "Pushed ${updates.size} ref(s)")
                } else {
                    GitSyncResult(false, "Rejected: ${rejected.status} ${rejected.message.orEmpty()}".trim())
                }
            }
        }.also { refreshQuietly(repoDir) }.getOrElse { GitSyncResult(false, it.messageOrClass()) }
    }

    override suspend fun pull(repoDir: File): GitSyncResult = withContext(io) {
        runCatching {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                val remote = BranchConfig(repo.config, repo.branch).remote ?: Constants.DEFAULT_REMOTE_NAME
                val result = git.pull()
                    .setRemote(remote)
                    .setCredentialsProvider(credentialProviderFor(remoteUrl(repo, remote), null))
                    .call()
                if (result.isSuccessful) {
                    GitSyncResult(true, result.mergeResult?.mergeStatus?.toString() ?: "Up to date")
                } else {
                    GitSyncResult(false, result.mergeResult?.mergeStatus?.toString() ?: "Pull failed")
                }
            }
        }.also { refreshQuietly(repoDir) }.getOrElse { GitSyncResult(false, it.messageOrClass()) }
    }

    override suspend fun isRepository(repoDir: File): Boolean = withContext(io) { isRepo(repoDir) }

    override suspend fun remoteInfo(repoDir: File): GitRemoteInfo? = withContext(io) {
        if (!isRepo(repoDir)) return@withContext null
        runCatching {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                // Detached HEAD / unborn branch → no ref to build from.
                val branch = repo.branch?.takeIf { it.isNotBlank() && it != repo.resolve(Constants.HEAD)?.name }
                    ?: return@runCatching null
                val remote = BranchConfig(repo.config, branch).remote ?: Constants.DEFAULT_REMOTE_NAME
                val url = remoteUrl(repo, remote)?.takeIf { it.isNotBlank() } ?: return@runCatching null
                GitRemoteInfo(url = url, ref = branch)
            }
        }.getOrNull()
    }

    override suspend fun init(repoDir: File): Unit = withContext(io) {
        repoDir.mkdirs()
        Git.init().setDirectory(repoDir).call().close()
        flowForOrCompute(repoDir).value = computeState(repoDir)
    }

    // region internals

    private fun flowForOrCompute(repoDir: File): MutableStateFlow<GitState> =
        states.getOrPut(key(repoDir)) { MutableStateFlow(computeState(repoDir)) }

    private suspend fun mutate(repoDir: File, block: (Git) -> Unit) = withContext(io) {
        Git.open(repoDir).use(block)
        flowForOrCompute(repoDir).value = computeState(repoDir)
    }

    private fun refreshQuietly(repoDir: File) {
        runCatching { flowForOrCompute(repoDir).value = computeState(repoDir) }
    }

    private fun computeState(repoDir: File): GitState {
        val message = commitMessages[key(repoDir)].orEmpty()
        if (!isRepo(repoDir)) {
            return GitState(branch = "", changes = emptyList(), commitMessage = message, isRepository = false)
        }
        return Git.open(repoDir).use { git ->
            val repo = git.repository
            val status = git.status().call()
            val changes = buildList {
                status.added.forEach { add(GitChange(it, GitFileStatus.ADDED, staged = true)) }
                status.changed.forEach { add(GitChange(it, GitFileStatus.MODIFIED, staged = true)) }
                status.removed.forEach { add(GitChange(it, GitFileStatus.DELETED, staged = true)) }
                status.modified.forEach { add(GitChange(it, GitFileStatus.MODIFIED, staged = false)) }
                status.missing.forEach { add(GitChange(it, GitFileStatus.DELETED, staged = false)) }
                status.untracked.forEach { add(GitChange(it, GitFileStatus.UNTRACKED, staged = false)) }
            }
            val tracking = runCatching { BranchTrackingStatus.of(repo, repo.branch) }.getOrNull()
            GitState(
                branch = repo.branch ?: "HEAD",
                changes = changes,
                commitMessage = message,
                ahead = tracking?.aheadCount,
                behind = tracking?.behindCount,
                isRepository = true,
            )
        }
    }

    private fun headTreeIterator(repo: Repository): org.eclipse.jgit.treewalk.AbstractTreeIterator {
        val head = repo.resolve("HEAD^{tree}") ?: return EmptyTreeIterator()
        return CanonicalTreeParser(null, repo.newObjectReader(), head)
    }

    private fun identityFor(repo: Repository): Pair<String, String> {
        val config = repo.config
        val name = config.getString("user", null, "name")?.takeIf { it.isNotBlank() } ?: DEFAULT_AUTHOR_NAME
        val email = config.getString("user", null, "email")?.takeIf { it.isNotBlank() } ?: DEFAULT_AUTHOR_EMAIL
        return name to email
    }

    private fun credentialProviderFor(url: String?, explicit: GitCredentials?): UsernamePasswordCredentialsProvider? {
        val credentials = explicit?.takeIf { it.token.isNotBlank() }
            ?: url?.let { credentialStore.credentialsForUrl(it) }
            ?: return null
        val username = credentials.username.ifBlank { DEFAULT_TOKEN_USERNAME }
        return UsernamePasswordCredentialsProvider(username, credentials.token)
    }

    private fun remoteUrl(repo: Repository, remote: String): String? =
        repo.config.getString("remote", remote, "url")

    private fun uniqueDestination(name: String): File {
        val home = projectsHome().apply { mkdirs() }
        var candidate = File(home, name)
        var counter = 2
        while (candidate.exists() && candidate.list()?.isNotEmpty() == true) {
            candidate = File(home, "$name-${counter++}")
        }
        return candidate
    }

    private fun isRepo(repoDir: File): Boolean = File(repoDir, Constants.DOT_GIT).exists()

    private fun key(repoDir: File): String = runCatching { repoDir.canonicalPath }.getOrDefault(repoDir.path)

    private fun RevCommit.toGitCommit(): GitCommit = GitCommit(
        id = name,
        shortId = abbreviate(7).name(),
        message = shortMessage,
        authorName = authorIdent.name,
        authorEmail = authorIdent.emailAddress,
        timeMillis = commitTime.toLong() * 1000L,
    )

    private companion object {
        const val DEFAULT_AUTHOR_NAME = "Android Studio Lite"
        const val DEFAULT_AUTHOR_EMAIL = "asl@localhost"
        const val DEFAULT_TOKEN_USERNAME = "x-access-token"

        val ACCEPTED_PUSH_STATUSES = setOf(
            RemoteRefUpdate.Status.OK,
            RemoteRefUpdate.Status.UP_TO_DATE,
        )

        fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }

        fun deriveRepoName(url: String): String =
            url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "repository" }

        fun Throwable.messageOrClass(): String = message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
    }
}

/**
 * Parse a unified diff (as produced by [DiffFormatter]) into [GitDiffLine]s with correct old/new line
 * numbers. Header lines (`diff --git`, `index`, `---`, `+++`) are skipped; `@@` hunk headers reset the
 * running line counters.
 */
internal fun parseUnifiedDiff(patch: String): List<GitDiffLine> {
    if (patch.isBlank()) return emptyList()
    val hunkHeader = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
    val result = mutableListOf<GitDiffLine>()
    var oldNo = 0
    var newNo = 0
    for (line in patch.split('\n')) {
        when {
            line.startsWith("@@") -> {
                val match = hunkHeader.find(line) ?: continue
                oldNo = match.groupValues[1].toInt()
                newNo = match.groupValues[2].toInt()
            }
            line.startsWith("diff ") || line.startsWith("index ") ||
                line.startsWith("--- ") || line.startsWith("+++ ") ||
                line.startsWith("new file") || line.startsWith("deleted file") ||
                line.startsWith("similarity ") || line.startsWith("rename ") ||
                line.startsWith("\\") -> Unit
            line.startsWith("+") -> result.add(GitDiffLine(GitDiffKind.ADDED, line.substring(1), newNo = newNo++))
            line.startsWith("-") -> result.add(GitDiffLine(GitDiffKind.REMOVED, line.substring(1), oldNo = oldNo++))
            oldNo > 0 || newNo > 0 -> {
                val text = if (line.startsWith(" ")) line.substring(1) else line
                result.add(GitDiffLine(GitDiffKind.CONTEXT, text, oldNo = oldNo++, newNo = newNo++))
            }
        }
    }
    return result
}
