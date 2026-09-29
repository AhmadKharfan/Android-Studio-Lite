package com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot

import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import kotlin.coroutines.coroutineContext

/** A project snapshot on the remote: branch [branch] points at commit [sha]. */
data class SourceSnapshot(val branch: String, val sha: String)

class SnapshotException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Publishes a project's current files to the build repository as a single commit, without touching
 * the project's own Git repository (if it has one).
 *
 * Each project gets a private bare "shadow" repository under [cacheRoot]. A snapshot is an orphan
 * commit of the project files force-pushed to `asl/src/<projectKey>`. Because the previous snapshot is
 * still known on both sides, a push only transfers files that changed since the last build, while the
 * branch never accumulates history.
 *
 * @param maxFileBytes GitHub rejects files over 100 MB; a larger file fails the snapshot up front.
 */
class SourceSnapshotPusher(
    private val cacheRoot: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Long = MAX_GITHUB_FILE_BYTES,
) {

    private val locks = ConcurrentHashMap<String, Mutex>()

    /** A stable, ref-safe key for [projectRoot]. */
    fun projectKey(projectRoot: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(projectRoot.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(KEY_LENGTH)

    /** The branch holding [projectKey]'s snapshots, as the workflow expects it. */
    fun branchFor(projectKey: String): String = "${BuildWorkflow.SOURCE_REF_PREFIX}$projectKey"

    /**
     * Commits [projectRoot] and pushes it to [remoteUrl] over HTTPS, authenticating with [token].
     *
     * @throws SnapshotException when the project can't be snapshotted or the push is rejected.
     */
    suspend fun push(projectRoot: File, remoteUrl: String, token: String, projectName: String): SourceSnapshot {
        require(projectRoot.isDirectory) { "Not a directory: $projectRoot" }
        val key = projectKey(projectRoot)
        return locks.getOrPut(key) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                openShadow(key).use { repo ->
                    val branch = branchFor(key)
                    val commit = commitSnapshot(repo, projectRoot, projectName, Constants.R_HEADS + branch)
                    pushRef(repo, Constants.R_HEADS + branch, remoteUrl, token)
                    SourceSnapshot(branch, commit.name)
                }
            }
        }
    }

    private fun openShadow(key: String): Repository {
        val dir = File(cacheRoot, "$key.git")
        val repo = FileRepositoryBuilder().setGitDir(dir).setBare().build()
        if (!File(dir, "HEAD").isFile) repo.create(true)
        return repo
    }

    private suspend fun commitSnapshot(repo: Repository, root: File, projectName: String, ref: String): ObjectId {
        repo.newObjectInserter().use { inserter ->
            val index = DirCache.newInCore()
            val builder = index.builder()
            addDirectory(root, root, inserter) { entry -> builder.add(entry) }
            builder.finish()
            if (index.entryCount == 0) throw SnapshotException("The project folder is empty.")
            val tree = index.writeTree(inserter)
            val ident = PersonIdent(AUTHOR_NAME, AUTHOR_EMAIL, nowMillis(), 0)
            val commit = inserter.insert(
                CommitBuilder().apply {
                    setTreeId(tree)
                    author = ident
                    committer = ident
                    message = "Snapshot of $projectName\n"
                },
            )
            inserter.flush()
            val update = repo.updateRef(ref).apply {
                setNewObjectId(commit)
                isForceUpdate = true
            }
            val result = update.update()
            if (result !in SUCCESSFUL_REF_UPDATES) throw SnapshotException("Couldn't record the snapshot ($result)")
            return commit
        }
    }

    private suspend fun addDirectory(
        root: File,
        dir: File,
        inserter: ObjectInserter,
        add: (DirCacheEntry) -> Unit,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            coroutineContext.ensureActive()
            when {
                isSymbolicLink(child) -> continue
                child.isDirectory -> if (SnapshotFileFilter.includesDirectory(child)) addDirectory(root, child, inserter, add)
                child.isFile -> if (SnapshotFileFilter.includesFile(root, child)) add(entryFor(root, child, inserter))
            }
        }
    }

    // The executable bit isn't reliable on Android storage (or Windows), so decide from the file itself.
    private fun isScript(file: File): Boolean =
        file.name == "gradlew" || file.name.endsWith(".sh") ||
            file.inputStream().use { it.read() == '#'.code && it.read() == '!'.code }

    // java.nio.file needs API 26; a link resolves somewhere other than where it sits.
    private fun isSymbolicLink(file: File): Boolean = runCatching {
        file.canonicalFile != File(file.parentFile?.canonicalFile, file.name)
    }.getOrDefault(true)

    private fun entryFor(root: File, file: File, inserter: ObjectInserter): DirCacheEntry {
        val path = file.relativeTo(root).invariantSeparatorsPath
        val length = file.length()
        if (length > maxFileBytes) {
            throw SnapshotException(
                "$path is ${length / BYTES_PER_MB} MB. GitHub doesn't accept files over ${maxFileBytes / BYTES_PER_MB} MB.",
            )
        }
        val blob = file.inputStream().use { inserter.insert(Constants.OBJ_BLOB, length, it) }
        return DirCacheEntry(path).apply {
            fileMode = if (isScript(file)) FileMode.EXECUTABLE_FILE else FileMode.REGULAR_FILE
            setLength(length)
            setObjectId(blob)
        }
    }

    private fun pushRef(repo: Repository, ref: String, remoteUrl: String, token: String) {
        val update = try {
            Git.wrap(repo).push()
                .setRemote(remoteUrl)
                .setRefSpecs(RefSpec("+$ref:$ref"))
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(TOKEN_USERNAME, token))
                .setProgressMonitor(NullProgressMonitor.INSTANCE)
                .call()
                .flatMap { it.remoteUpdates }
                .firstOrNull { it.remoteName == ref }
        } catch (e: GitAPIException) {
            throw SnapshotException("Couldn't upload the project to GitHub: ${e.message}", e)
        }
        rejectionOf(update)?.let { throw SnapshotException(it) }
    }

    private fun rejectionOf(update: RemoteRefUpdate?): String? = when {
        update == null -> "GitHub didn't report the upload result"
        update.status == RemoteRefUpdate.Status.OK || update.status == RemoteRefUpdate.Status.UP_TO_DATE -> null
        else -> "GitHub rejected the upload (${update.status}${update.message?.let { ": $it" }.orEmpty()})"
    }

    companion object {
        const val MAX_GITHUB_FILE_BYTES = 100L * 1024 * 1024
        private const val BYTES_PER_MB = 1024 * 1024
        private const val KEY_LENGTH = 32
        private const val TOKEN_USERNAME = "x-access-token"
        private const val AUTHOR_NAME = "Android Studio Lite"
        private const val AUTHOR_EMAIL = "builds@androidstudiolite.invalid"
        private val SUCCESSFUL_REF_UPDATES = setOf(
            RefUpdate.Result.NEW,
            RefUpdate.Result.FORCED,
            RefUpdate.Result.FAST_FORWARD,
            RefUpdate.Result.NO_CHANGE,
        )
    }
}
