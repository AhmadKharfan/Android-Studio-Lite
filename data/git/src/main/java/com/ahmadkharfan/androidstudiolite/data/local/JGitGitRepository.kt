package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfigState
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitDetails
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitOperationType
import com.ahmadkharfan.androidstudiolite.domain.model.GitLogPage
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmoduleStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.GitUpstream
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.RootInvalidationReason
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteGate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RefLeaseSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.submodule.SubmoduleStatusType
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.eclipse.jgit.util.FS
import java.io.File
import java.nio.charset.StandardCharsets
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class JGitGitRepository(
    private val credentialStore: GitCredentialStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val operationCoordinator: GitOperationCoordinator = GitOperationCoordinator(),
    private val fileChangeBus: FileChangeBus = FileChangeBus(),
    private val authorStore: GitAuthorStore = InMemoryGitAuthorStore(),
    private val workspaceWriteGate: WorkspaceWriteGate = DefaultWorkspaceWriteGate(),
) : GitRepository {

    private val commitMessages = ConcurrentHashMap<String, String>()
    private val refreshRuntimes = ConcurrentHashMap<String, RepoRefreshRuntime>()
    private val refreshScope = CoroutineScope(SupervisorJob() + io)
    private val statusComputer = JGitStatusComputer()
    private val diffEngine = JGitDiffEngine()
    private val historyEngine = JGitHistoryEngine()
    private val stashEngine = JGitStashEngine()
    private val branchEngine = JGitBranchEngine()
    private val remoteEngine = JGitRemoteEngine()

    override fun clone(
        url: String,
        destination: File,
        options: CloneOptions,
        credentials: GitCredentials?,
    ): Flow<CloneProgress> =
        callbackFlow {
            require(!destination.exists()) { "Clone destination already exists: ${destination.absolutePath}" }
            val cleanUrl = url.trim()
            hostOf(cleanUrl)?.let { host ->
                credentials?.takeIf { it.token.isNotBlank() }?.let { credentialStore.save(host, it) }
            }
            val cancelled = AtomicBoolean(false)
            val collectionJob = coroutineContext.job
            val monitor = cloneProgressMonitor(cancelled, collectionJob) { progress ->
                trySendBlocking(progress)
            }
            trySendBlocking(
                CloneProgress(fraction = 0f, message = "Resolving ${GitUrlRedactor.stripUserInfo(cleanUrl)}"),
            )
            try {
                configureCloneCommand(cleanUrl, destination, options, credentials, monitor)
                    .call()
                    .close()
                if (monitor.isCancelled()) throw CancellationException("Clone cancelled")
                close()
            } catch (t: Throwable) {
                destination.deleteRecursively()
                if (monitor.isCancelled() || t is CancellationException) {
                    val cancellation = CancellationException("Clone cancelled")
                    cancellation.initCause(t)
                    close(cancellation)
                } else {
                    close(JGitExceptionMapper.map(t, cleanUrl))
                }
            }
            awaitClose { cancelled.set(true) }
        }.flowOn(io)

    private fun cloneProgressMonitor(
        cancelled: AtomicBoolean,
        collectionJob: Job,
        emit: (CloneProgress) -> Unit,
    ): EmptyProgressMonitor = object : EmptyProgressMonitor() {
        private var task = ""
        private var total = 0
        private var done = 0
        override fun beginTask(title: String?, totalWork: Int) {
            task = title.orEmpty()
            total = totalWork
            done = 0
            emitProgress(0)
        }
        override fun update(completed: Int) {
            done += completed
            emitProgress(done)
        }
        override fun isCancelled(): Boolean = cancelled.get() || collectionJob.isCancelled
        private fun emitProgress(current: Int) {
            val fraction = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else null
            emit(CloneProgress(fraction = fraction, message = task.ifBlank { "Cloning" }))
        }
    }

    private fun configureCloneCommand(
        cleanUrl: String,
        destination: File,
        options: CloneOptions,
        credentials: GitCredentials?,
        monitor: EmptyProgressMonitor,
    ) = Git.cloneRepository()
        .setURI(cleanUrl)
        .setDirectory(destination)
        .apply {
            options.depth?.let { setDepth(it) }
            options.branch?.takeIf { it.isNotBlank() }?.let { branch ->
                setBranch(branch)
                if (options.singleBranch) {
                    setBranchesToClone(listOf(branch.toFullBranchRef()))
                }
            }
            if (options.singleBranch) setCloneAllBranches(false)
            setCloneSubmodules(options.recursiveSubmodules)
        }
        .setCredentialsProvider(credentialProviderFor(cleanUrl, credentials))
        .setProgressMonitor(monitor)

    override fun observeState(repoDir: File): StateFlow<GitState> = refreshRuntime(repoDir).state

    override suspend fun refresh(repoDir: File, includeIgnored: Boolean) = withContext(io) {
        val runtime = refreshRuntime(repoDir)
        runtime.includeIgnored = includeIgnored
        runtime.pipeline.requestImmediate(includeIgnored)
    }

    override suspend fun onAppForegrounded(repoDir: File) = withContext(io) {
        val runtime = refreshRuntime(repoDir)
        runtime.pipeline.requestImmediate(runtime.includeIgnored)
    }

    override suspend fun diffIndexToWorktree(repoDir: File, path: String, force: Boolean): GitFileDiff =
        withContext(io) { openGit(repoDir).use { diffEngine.indexToWorktree(it.repository, path, force) } }

    override suspend fun diffHeadToIndex(repoDir: File, path: String, force: Boolean): GitFileDiff =
        withContext(io) { openGit(repoDir).use { diffEngine.headToIndex(it.repository, path, force) } }

    override suspend fun diffCommitToParent(
        repoDir: File,
        commitId: String,
        path: String,
        force: Boolean,
    ): GitFileDiff = withContext(io) {
        openGit(repoDir).use { diffEngine.commitToParent(it.repository, commitId, path, force) }
    }

    override suspend fun diffIndexToBuffer(repoDir: File, path: String, buffer: String): GitFileDiff =
        withContext(io) { openGit(repoDir).use { diffEngine.indexToBuffer(it.repository, path, buffer) } }

    override suspend fun stageHunk(repoDir: File, path: String, hunk: GitDiffHunk) =
        mutate(repoDir, GitOperationType.PARTIAL_STAGE) { git ->
            diffEngine.updateIndex(git.repository, path, hunk, reverse = false)
        }

    override suspend fun unstageHunk(repoDir: File, path: String, hunk: GitDiffHunk) =
        mutate(repoDir, GitOperationType.PARTIAL_STAGE) { git ->
            diffEngine.updateIndex(git.repository, path, hunk, reverse = true)
        }

    override suspend fun stage(repoDir: File, path: String) = mutate(repoDir, GitOperationType.STAGE) { git ->

        if (File(repoDir, path).exists()) {
            git.add().addFilepattern(path).call()
        } else {
            git.rm().addFilepattern(path).setCached(true).call()
        }
    }

    override suspend fun unstage(repoDir: File, path: String) = mutate(repoDir, GitOperationType.UNSTAGE) { git ->
        git.reset().addPath(path).call()
    }

    override suspend fun stageAll(repoDir: File) = mutate(repoDir, GitOperationType.STAGE) { git ->
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
    }

    override suspend fun unstageAll(repoDir: File) = mutate(repoDir, GitOperationType.UNSTAGE) { git ->
        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.MIXED).call()
    }

    override suspend fun setCommitMessage(repoDir: File, message: String) = withContext(io) {
        commitMessages[key(repoDir)] = message
        val flow = refreshRuntimes[key(repoDir)]?.state ?: return@withContext
        flow.value = flow.value.copy(commitMessage = message)
    }

    override suspend fun commit(repoDir: File, amend: Boolean): String = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.COMMIT) {
            val id = openGit(repoDir).use { git ->
                val identity = identityFor(git.repository)
                git.commit()
                    .setMessage(commitMessages[key(repoDir)].orEmpty())
                    .setAmend(amend)
                    .setAuthor(identity.name, identity.email)
                    .setCommitter(identity.name, identity.email)
                    .call()
                    .name
            }
            commitMessages.remove(key(repoDir))
            refreshAfterGitOperation(repoDir)
            id
        }
    }

    override suspend fun getAuthorConfig(repoDir: File): GitAuthorConfigState = withContext(io) {
        val appGlobal = authorStore.get()
        val local = openGit(repoDir).use { localIdentity(it.repository) }
        GitAuthorConfigState(
            effective = local ?: appGlobal ?: FALLBACK_AUTHOR,
            local = local,
            appGlobal = appGlobal,
        )
    }

    override suspend fun setLocalAuthor(repoDir: File, config: GitAuthorConfig?) = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.CONFIGURE) {
            openGit(repoDir).use { git ->
                val localConfig = git.repository.localConfig()
                if (config == null) {
                    localConfig.unset("user", null, "name")
                    localConfig.unset("user", null, "email")
                } else {
                    localConfig.setString("user", null, "name", config.name.trim())
                    localConfig.setString("user", null, "email", config.email.trim())
                }
                localConfig.save()
            }
        }
    }

    override suspend fun setAppGlobalAuthor(config: GitAuthorConfig) {
        authorStore.set(config)
    }

    override suspend fun branches(repoDir: File): List<GitBranch> = withContext(io) {
        if (!isRepo(repoDir)) return@withContext emptyList()
        openGit(repoDir).use { branchEngine.list(it) }
    }

    override suspend fun createBranch(repoDir: File, name: String, checkout: Boolean) =
        mutate(
            repoDir,
            GitOperationType.CREATE_BRANCH,
            invalidatesRoot = checkout,
        ) { git -> branchEngine.create(git, name, checkout) }

    override suspend fun checkout(repoDir: File, name: String) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        mutate(repoDir, GitOperationType.CHECKOUT, invalidatesRoot = true) { git ->
            branchEngine.checkout(git, name)
        }
    }

    override suspend fun checkoutRemoteBranch(repoDir: File, remoteBranch: String, localName: String?) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        mutate(repoDir, GitOperationType.CHECKOUT, invalidatesRoot = true) { git ->
            branchEngine.checkoutRemote(git, remoteBranch, localName)
        }
    }

    override suspend fun renameBranch(repoDir: File, oldName: String, newName: String) =
        mutate(repoDir, GitOperationType.BRANCH) { git ->
            branchEngine.rename(git, oldName, newName)
        }

    override suspend fun deleteBranch(repoDir: File, name: String, force: Boolean) = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.BRANCH) {
            try {
                openGit(repoDir).use { branchEngine.delete(it, name, force) }
                refreshAfterGitOperation(repoDir)
            } catch (error: GitException) {
                throw error
            } catch (error: Throwable) {
                throw JGitExceptionMapper.map(error)
            }
        }
    }

    override suspend fun publishBranch(repoDir: File, name: String): GitSyncResult = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.PUSH, cancellable = true) {
            var url: String? = null
            try {
                openGit(repoDir).use { git ->
                    val repo = git.repository
                    val upstream = upstreamFor(repo, name)
                    val remote = upstream?.remote ?: Constants.DEFAULT_REMOTE_NAME
                    val remoteBranch = upstream?.remoteBranch ?: name
                    val remoteRef = "${Constants.R_HEADS}$remoteBranch"
                    url = remoteUrl(repo, remote)
                    val updates = git.push()
                        .setRemote(remote)
                        .setRefSpecs(RefSpec("${Constants.R_HEADS}$name:$remoteRef"))
                        .setCredentialsProvider(credentialProviderFor(url, null))
                        .setProgressMonitor(operationProgressMonitor(repoDir))
                        .call().flatMap { it.remoteUpdates }
                    ensureOperationActive(repoDir)
                    updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }?.let {
                        throw pushFailure(it, url, forceWithLease = false)
                    }
                    if (upstream == null) {
                        repo.config.setString("branch", name, "remote", remote)
                        repo.config.setString("branch", name, "merge", remoteRef)
                        repo.config.save()
                    }
                    GitSyncResult(true, "Published $name")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                throwMappedOrCancelled(repoDir, error, url)
            } finally {
                refreshQuietly(repoDir)
            }
        }
    }

    override suspend fun log(repoDir: File, max: Int): List<GitCommit> = withContext(io) {
        if (!isRepo(repoDir)) return@withContext emptyList()
        openGit(repoDir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) return@withContext emptyList()
            git.log().setMaxCount(max).call().map { it.toGitCommit() }
        }
    }

    override suspend fun log(repoDir: File, cursor: String?, limit: Int): GitLogPage = withContext(io) {
        if (!isRepo(repoDir)) return@withContext GitLogPage(emptyList(), null)
        openGit(repoDir).use { historyEngine.log(it.repository, cursor, limit) }
    }

    override suspend fun fileHistory(repoDir: File, path: String, cursor: String?, limit: Int): GitLogPage =
        withContext(io) {
            if (!isRepo(repoDir)) return@withContext GitLogPage(emptyList(), null)
            openGit(repoDir).use { historyEngine.fileHistory(it.repository, path, cursor, limit) }
        }

    override suspend fun commitDetails(repoDir: File, commitId: String): GitCommitDetails = withContext(io) {
        openGit(repoDir).use { historyEngine.commitDetails(it.repository, commitId) }
    }

    override suspend fun blame(repoDir: File, path: String): List<GitBlameLine> = withContext(io) {
        openGit(repoDir).use { historyEngine.blame(it, path) }
    }

    override suspend fun isShallow(repoDir: File): Boolean = withContext(io) {
        if (!isRepo(repoDir)) return@withContext false
        openGit(repoDir).use { it.repository.objectDatabase.shallowCommits.isNotEmpty() }
    }

    override suspend fun deepen(repoDir: File): GitSyncResult = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.DEEPEN, cancellable = true) {
            var url: String? = null
            try {
                val result = openGit(repoDir).use { git ->
                    if (git.repository.objectDatabase.shallowCommits.isEmpty()) {
                        return@use GitSyncResult(true, "History is already complete")
                    }
                    val remote = trackingRemote(git.repository, git.repository.branch)
                    url = remoteUrl(git.repository, remote)
                    val fetched = git.fetch()
                        .setRemote(remote)
                        .setUnshallow(true)
                        .setCredentialsProvider(credentialProviderFor(url, null))
                        .setProgressMonitor(operationProgressMonitor(repoDir))
                        .call()
                    ensureOperationActive(repoDir)
                    GitSyncResult(true, "Deepened history (${fetched.trackingRefUpdates.size} ref update(s))")
                }
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                throwMappedOrCancelled(repoDir, error, url)
            } finally {
                refreshQuietly(repoDir)
            }
        }
    }

    override suspend fun listTags(repoDir: File): List<GitTag> = withContext(io) {
        openGit(repoDir).use { historyEngine.listTags(it.repository) }
    }

    override suspend fun createTag(repoDir: File, name: String, message: String?, targetCommit: String?) {
        val identity = withContext(io) { openGit(repoDir).use { identityFor(it.repository) } }
        mutate(repoDir, GitOperationType.TAG) { git ->
            val command = git.tag().setName(name)
            if (message == null) command.setAnnotated(false) else {
                command.setAnnotated(true).setMessage(message)
                    .setTagger(org.eclipse.jgit.lib.PersonIdent(identity.name, identity.email))
            }
            targetCommit?.takeIf { it.isNotBlank() }?.let { target ->
                org.eclipse.jgit.revwalk.RevWalk(git.repository).use { walk ->
                    val objectId = git.repository.resolve(target)
                        ?: throw GitException.Unknown("Unknown tag target: $target")
                    command.setObjectId(walk.parseAny(objectId))
                }
            }
            command.call()
        }
    }

    override suspend fun deleteTag(repoDir: File, name: String) = mutate(repoDir, GitOperationType.TAG) { git ->
        git.tagDelete().setTags(name).call()
    }

    override suspend fun pushTag(repoDir: File, name: String): GitSyncResult =
        pushTags(repoDir, RefSpec("${Constants.R_TAGS}$name:${Constants.R_TAGS}$name"), label = name)

    override suspend fun pushAllTags(repoDir: File): GitSyncResult = pushTags(repoDir, refSpec = null, label = "tags")

    override suspend fun stashCreate(repoDir: File, message: String?, includeUntracked: Boolean): String? =
        withContext(io) {
            workspaceWriteGate.prepareForWorktreeMutation(repoDir)
            operationCoordinator.runExclusive(repoDir, GitOperationType.STASH) {
                val id = openGit(repoDir).use { git ->
                    val identity = identityFor(git.repository)
                    stashEngine.create(
                        git,
                        org.eclipse.jgit.lib.PersonIdent(identity.name, identity.email),
                        message,
                        includeUntracked,
                    )
                }
                refreshAfterGitOperation(repoDir)
                fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
                id?.name
            }
        }

    override suspend fun stashList(repoDir: File): List<GitStash> = withContext(io) {
        openGit(repoDir).use { stashEngine.list(it) }
    }

    override suspend fun stashApply(repoDir: File, index: Int) = applyStash(repoDir, index, pop = false)

    override suspend fun stashPop(repoDir: File, index: Int) = applyStash(repoDir, index, pop = true)

    override suspend fun stashDrop(repoDir: File, index: Int) = mutate(repoDir, GitOperationType.STASH) { git ->
        stashEngine.drop(git, index)
    }

    override suspend fun merge(
        repoDir: File,
        ref: String,
        ffMode: GitFastForwardMode,
        message: String?,
    ): GitIntegrationResult = integration(repoDir, GitOperationType.MERGE, needsIdentity = true) { git ->
        val target = git.repository.resolve(ref) ?: throw GitException.Unknown("Unknown ref: $ref")
        val command = git.merge().include(target).setProgressMonitor(operationProgressMonitor(repoDir)).setFastForward(
            when (ffMode) {
                GitFastForwardMode.FF_ALLOWED -> MergeCommand.FastForwardMode.FF
                GitFastForwardMode.FF_ONLY -> MergeCommand.FastForwardMode.FF_ONLY
                GitFastForwardMode.NO_FF -> MergeCommand.FastForwardMode.NO_FF
            },
        )
        message?.takeIf { it.isNotBlank() }?.let(command::setMessage)
        command.call().toIntegrationResult()
    }

    override suspend fun mergeAbort(repoDir: File) = abortToOrigHead(repoDir, GitOperationType.MERGE)

    override suspend fun cherryPick(repoDir: File, commitId: String): GitIntegrationResult =
        integration(repoDir, GitOperationType.CHERRY_PICK, needsIdentity = true) { git ->
            val id = git.repository.resolve(commitId) ?: throw GitException.Unknown("Unknown commit: $commitId")
            val result = git.cherryPick().include(id).setProgressMonitor(operationProgressMonitor(repoDir)).call()
            when (result.status.name) {
                "OK" -> GitIntegrationResult(GitIntegrationStatus.APPLIED, result.newHead?.name)
                "CONFLICTING" -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS)
                else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, detail = result.status.name)
            }
        }

    override suspend fun cherryPickAbort(repoDir: File) = abortToHead(repoDir, GitOperationType.CHERRY_PICK)

    override suspend fun revert(repoDir: File, commitId: String): GitIntegrationResult =
        integration(repoDir, GitOperationType.REVERT, needsIdentity = true) { git ->
            val id = git.repository.resolve(commitId) ?: throw GitException.Unknown("Unknown commit: $commitId")
            val command = git.revert().include(id).setProgressMonitor(operationProgressMonitor(repoDir))
            val result = command.call()
            when {
                result != null -> GitIntegrationResult(GitIntegrationStatus.APPLIED, result.name)
                !command.unmergedPaths.isNullOrEmpty() -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS)
                else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, detail = command.failingResult?.mergeStatus?.name)
            }
        }

    override suspend fun revertAbort(repoDir: File) = abortToHead(repoDir, GitOperationType.REVERT)

    override suspend fun rebase(repoDir: File, upstreamRef: String): GitIntegrationResult =
        runRebase(repoDir, RebaseCommand.Operation.BEGIN, upstreamRef)

    override suspend fun rebaseContinue(repoDir: File): GitIntegrationResult =
        runRebase(repoDir, RebaseCommand.Operation.CONTINUE)

    override suspend fun rebaseSkip(repoDir: File): GitIntegrationResult =
        runRebase(repoDir, RebaseCommand.Operation.SKIP)

    override suspend fun rebaseAbort(repoDir: File): GitIntegrationResult =
        runRebase(repoDir, RebaseCommand.Operation.ABORT)

    override suspend fun conflictEntries(repoDir: File): List<GitConflictEntry> = withContext(io) {
        openGit(repoDir).use { git ->
            val cache = git.repository.readDirCache()
            (0 until cache.entryCount).asSequence().map(cache::getEntry)
                .filter { it.stage > 0 }.map { it.pathString }
                .distinct().sorted().map { path ->
                    GitConflictEntry(
                        path = path,
                        base = cache.stageText(git.repository, path, 1),
                        ours = cache.stageText(git.repository, path, 2),
                        theirs = cache.stageText(git.repository, path, 3),
                        worktree = File(repoDir, path).takeIf(File::isFile)?.readText(),
                    )
                }.toList()
        }
    }

    override suspend fun resolveAcceptOurs(repoDir: File, path: String) = resolveStage(repoDir, path, ours = true)

    override suspend fun resolveAcceptTheirs(repoDir: File, path: String) = resolveStage(repoDir, path, ours = false)

    override suspend fun markResolved(repoDir: File, path: String) = integrationUnit(repoDir, GitOperationType.RESOLVE) { git ->
        if (File(repoDir, path).exists()) git.add().addFilepattern(path).call()
        else git.rm().setCached(true).addFilepattern(path).call()
    }

    override suspend fun restoreFiles(repoDir: File, paths: List<String>) {
        if (paths.isEmpty()) return
        integrationUnit(repoDir, GitOperationType.RESTORE) { git ->
            git.checkout().setStartPoint(Constants.HEAD).addPaths(paths).call()
        }
    }

    override suspend fun reset(repoDir: File, commitId: String, mode: GitResetMode) =
        integrationUnit(repoDir, GitOperationType.RESET) { git ->
            git.reset().setRef(commitId).setMode(
                when (mode) {
                    GitResetMode.SOFT -> org.eclipse.jgit.api.ResetCommand.ResetType.SOFT
                    GitResetMode.MIXED -> org.eclipse.jgit.api.ResetCommand.ResetType.MIXED
                    GitResetMode.HARD -> org.eclipse.jgit.api.ResetCommand.ResetType.HARD
                },
            ).call()
        }

    override suspend fun clean(repoDir: File, dryRun: Boolean, includeIgnored: Boolean): List<String> =
        withContext(io) {
            operationCoordinator.runExclusive(repoDir, GitOperationType.CLEAN) {
                if (!dryRun) workspaceWriteGate.prepareForWorktreeMutation(repoDir)
                val removed = openGit(repoDir).use { git ->
                    git.clean().setDryRun(dryRun).setCleanDirectories(true).setIgnore(!includeIgnored).call()
                        .map { it.replace('\\', '/') }.sorted()
                }
                if (!dryRun) {
                    refreshAfterGitOperation(repoDir)
                    fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
                }
                removed
            }
        }

    override suspend fun submodules(repoDir: File): List<GitSubmodule> = withContext(io) {
        openGit(repoDir).use { git ->
            val statuses = git.submoduleStatus().call()
            val urls = mutableMapOf<String, String>()
            val configuredUrls = mutableMapOf<String, String?>()
            val names = mutableMapOf<String, String>()
            SubmoduleWalk.forIndex(git.repository).use { walk ->
                while (walk.next()) {
                    urls[walk.path] = walk.remoteUrl.orEmpty()
                    configuredUrls[walk.path] = walk.configUrl
                    names[walk.path] = walk.moduleName
                }
            }
            statuses.map { (path, value) ->
                GitSubmodule(
                    name = names[path] ?: path.substringAfterLast('/'),
                    path = path,
                    url = urls[path]?.let(GitUrlRedactor::stripUserInfo).orEmpty(),
                    headId = value.headId?.name,
                    status = when {
                        value.type == SubmoduleStatusType.MISSING -> GitSubmoduleStatus.MISSING
                        value.headId != null -> GitSubmoduleStatus.CHECKED_OUT
                        configuredUrls[path] != null -> GitSubmoduleStatus.INITIALIZED
                        else -> GitSubmoduleStatus.UNINITIALIZED
                    },
                )
            }.sortedBy { it.path }
        }
    }

    override suspend fun submoduleInit(repoDir: File) = mutate(repoDir, GitOperationType.SUBMODULE) { git ->
        git.submoduleInit().call()
    }

    override suspend fun submoduleUpdate(repoDir: File) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        withContext(io) {
            operationCoordinator.runExclusive(repoDir, GitOperationType.SUBMODULE, cancellable = true) {
                try {
                    openGit(repoDir).use { git ->
                        val modules = git.submoduleStatus().call()
                        val urls = mutableMapOf<String, String>()
                        SubmoduleWalk.forIndex(git.repository).use { walk ->
                            while (walk.next()) urls[walk.path] = walk.remoteUrl.orEmpty()
                        }
                        modules.forEach { (path, _) ->
                            ensureOperationActive(repoDir)
                            git.submoduleUpdate()
                                .addPath(path)
                                .setCredentialsProvider(credentialProviderFor(urls[path], null))
                                .setProgressMonitor(operationProgressMonitor(repoDir))
                                .call()
                        }
                    }
                    ensureOperationActive(repoDir)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    throwMappedOrCancelled(repoDir, error, null)
                } finally {
                    withContext(NonCancellable) {
                        refreshQuietly(repoDir)
                        fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
                    }
                }
            }
        }
    }

    override suspend fun bootstrapRepository(repoDir: File, initialCommitMessage: String?): String? = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.INIT) {
            repoDir.mkdirs()
            if (!isRepo(repoDir)) Git.init().setDirectory(repoDir).call().close()
            writeAndroidGitignore(repoDir)
            val message = initialCommitMessage?.trim()?.takeIf { it.isNotEmpty() }
            val commitId = if (message == null) {
                null
            } else {
                openGit(repoDir).use { git ->
                    git.add().addFilepattern(".").call()
                    git.add().setUpdate(true).addFilepattern(".").call()
                    val identity = identityFor(git.repository)
                    git.commit()
                        .setMessage(message)
                        .setAuthor(identity.name, identity.email)
                        .setCommitter(identity.name, identity.email)
                        .call()
                        .name
                }
            }
            refreshAfterGitOperation(repoDir)
            fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
            commitId
        }
    }

    override suspend fun addToGitignore(repoDir: File, path: String) = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.CONFIGURE) {
            val root = repoDir.canonicalFile
            require(isRepo(root)) { "Not a Git repository" }
            val candidate = File(path).let { if (it.isAbsolute) it.canonicalFile else File(root, path).canonicalFile }
            require(candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)) {
                "Path is outside the repository"
            }
            val relative = candidate.relativeTo(root).invariantSeparatorsPath.trim('/').let {
                if (candidate.isDirectory) "$it/" else it
            }
            require(relative.isNotBlank()) { "The repository root cannot be ignored" }
            val ignore = File(root, ".gitignore")
            val existing = ignore.takeIf(File::exists)?.readLines().orEmpty()
            if (existing.none { it.trim() == relative }) {
                ignore.parentFile?.mkdirs()
                val prefix = if (ignore.exists() && ignore.length() > 0L && !ignore.readText().endsWith('\n')) "\n" else ""
                ignore.appendText("$prefix$relative\n")
            }
            refreshAfterGitOperation(repoDir)
            fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
        }
    }

    override suspend fun listRemotes(repoDir: File): List<GitRemote> = withContext(io) {
        try {
            openGit(repoDir).use { remoteEngine.list(it) }
        } catch (error: Throwable) {
            throw JGitExceptionMapper.map(error)
        }
    }

    override suspend fun addRemote(repoDir: File, name: String, url: String) =
        configureRemote(repoDir, url) { git -> remoteEngine.add(git, name, url) }

    override suspend fun setRemoteUrl(repoDir: File, name: String, url: String) =
        configureRemote(repoDir, url) { git -> remoteEngine.setUrl(git, name, url) }

    override suspend fun removeRemote(repoDir: File, name: String) =
        configureRemote(repoDir) { git -> remoteEngine.remove(git, name) }

    override suspend fun fetch(repoDir: File, remote: String?, prune: Boolean): GitSyncResult = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.FETCH, cancellable = true) {
            var url: String? = null
            try {
                val result = openGit(repoDir).use { git ->
                    val repo = git.repository
                    val selectedRemote = remote ?: trackingRemote(repo, repo.branch)
                    url = remoteUrl(repo, selectedRemote)
                    git.fetch()
                        .setRemote(selectedRemote)
                        .setRemoveDeletedRefs(prune)
                        .setCredentialsProvider(credentialProviderFor(url, null))
                        .setProgressMonitor(operationProgressMonitor(repoDir))
                        .call()
                }
                ensureOperationActive(repoDir)
                GitSyncResult(true, "Fetched ${result.trackingRefUpdates.size} ref update(s)")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                throwMappedOrCancelled(repoDir, error, url)
            } finally {
                refreshQuietly(repoDir)
            }
        }
    }

    override suspend fun setUpstream(repoDir: File, branch: String, remote: String, remoteBranch: String) =
        withContext(io) {
            operationCoordinator.runExclusive(repoDir, GitOperationType.CONFIGURE) {
                openGit(repoDir).use { git ->
                    remoteEngine.setUpstream(git.repository, branch, remote, remoteBranch)
                }
                refreshAfterGitOperation(repoDir)
            }
        }

    override suspend fun upstreamOf(repoDir: File, branch: String): GitUpstream? = withContext(io) {
        openGit(repoDir).use { remoteEngine.upstreamOf(it.repository, branch) }
    }

    override suspend fun push(repoDir: File, setUpstreamIfMissing: Boolean): GitSyncResult = withContext(io) {
        pushInternal(repoDir, setUpstreamIfMissing = setUpstreamIfMissing, forceWithLease = false)
    }

    override suspend fun pushForceWithLease(repoDir: File): GitSyncResult = withContext(io) {
        pushInternal(repoDir, setUpstreamIfMissing = false, forceWithLease = true)
    }

    private suspend fun pushInternal(
        repoDir: File,
        setUpstreamIfMissing: Boolean,
        forceWithLease: Boolean,
    ): GitSyncResult = operationCoordinator.runExclusive(repoDir, GitOperationType.PUSH, cancellable = true) {
        var url: String? = null
        try {
            val result = openGit(repoDir).use { git ->
                val repo = git.repository
                val branch = currentLocalBranch(repo)
                val upstream = upstreamFor(repo, branch)
                val remote = upstream?.remote ?: Constants.DEFAULT_REMOTE_NAME
                val remoteBranch = upstream?.remoteBranch ?: branch
                val remoteRef = "${Constants.R_HEADS}$remoteBranch"
                url = remoteUrl(repo, remote)
                val command = git.push()
                    .setRemote(remote)
                    .setRefSpecs(RefSpec("${Constants.R_HEADS}$branch:$remoteRef"))
                    .setCredentialsProvider(credentialProviderFor(url, null))
                    .setProgressMonitor(operationProgressMonitor(repoDir))
                if (forceWithLease) {
                    val trackingRef = repo.findRef("${Constants.R_REMOTES}$remote/$remoteBranch")
                        ?: throw GitException.StaleLease("No remote-tracking value for $remote/$remoteBranch; fetch first")
                    command
                        .setForce(true)
                        .setRefLeaseSpecs(RefLeaseSpec(remoteRef, trackingRef.objectId.name))
                }
                val updates = command.call().flatMap { it.remoteUpdates }
                ensureOperationActive(repoDir)
                val rejected = updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }
                if (rejected != null) throw pushFailure(rejected, url, forceWithLease)
                if (upstream == null && setUpstreamIfMissing) {
                    val config = repo.config
                    config.setString("branch", branch, "remote", remote)
                    config.setString("branch", branch, "merge", remoteRef)
                    config.save()
                }
                GitSyncResult(true, "Pushed ${updates.size} ref(s)")
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throwMappedOrCancelled(repoDir, error, url)
        } finally {
            refreshQuietly(repoDir)
        }
    }

    override suspend fun pull(repoDir: File, mode: PullMode): GitSyncResult = withContext(io) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        operationCoordinator.runExclusive(repoDir, GitOperationType.PULL, cancellable = true) {
            var url: String? = null
            var attempted = false
            try {
                openGit(repoDir).use { git ->
                    val repo = git.repository
                    val branch = currentLocalBranch(repo)
                    val upstream = upstreamFor(repo, branch)
                    val remote = upstream?.remote ?: Constants.DEFAULT_REMOTE_NAME
                    val remoteBranch = upstream?.remoteBranch ?: branch
                    url = remoteUrl(repo, remote)
                    attempted = true
                    val result = git.pull()
                        .setRemote(remote)
                        .setRemoteBranchName(remoteBranch)
                        .setRebase(mode == PullMode.REBASE)
                        .setCredentialsProvider(credentialProviderFor(url, null))
                        .setProgressMonitor(operationProgressMonitor(repoDir))
                        .call()
                    ensureOperationActive(repoDir)
                    pullResultToSyncResult(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                throwMappedOrCancelled(repoDir, error, url)
            } finally {
                withContext(NonCancellable) {
                    if (attempted) {
                        fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
                    }
                    refreshQuietly(repoDir)
                }
            }
        }
    }

    private fun pullResultToSyncResult(result: org.eclipse.jgit.api.PullResult): GitSyncResult {
        if (result.isSuccessful) {
            val detail = result.rebaseResult?.status?.toString()
                ?: result.mergeResult?.mergeStatus?.toString()
                ?: "Up to date"
            return GitSyncResult(true, detail)
        }
        val detail = result.rebaseResult?.status?.toString()
            ?: result.mergeResult?.mergeStatus?.toString()
            ?: "Pull failed"
        if (result.rebaseResult?.conflicts?.isNotEmpty() == true ||
            result.mergeResult?.mergeStatus?.isSuccessful == false
        ) {
            throw GitException.MergeConflict(detail)
        }
        throw GitException.Unknown(detail)
    }

    override suspend fun isRepository(repoDir: File): Boolean = withContext(io) { isRepo(repoDir) }

    override suspend fun remoteInfo(repoDir: File): GitRemoteInfo? = withContext(io) {
        if (!isRepo(repoDir)) return@withContext null
        runCatching {
            openGit(repoDir).use { git ->
                val repo = git.repository

                val branch = repo.branch?.takeIf { it.isNotBlank() && it != repo.resolve(Constants.HEAD)?.name }
                    ?: return@runCatching null
                val remote = BranchConfig(repo.config, branch).remote ?: Constants.DEFAULT_REMOTE_NAME
                val originalUrl = remoteUrl(repo, remote)?.takeIf { it.isNotBlank() } ?: return@runCatching null
                GitRemoteInfo(
                    url = GitUrlRedactor.stripUserInfo(originalUrl),
                    ref = branch,
                    requiresAuth = GitUrlRedactor.hasUserInfo(originalUrl) ||
                        credentialStore.credentialsForUrl(originalUrl) != null,
                )
            }
        }.getOrNull()
    }

    override suspend fun init(repoDir: File): Unit = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.INIT) {
            repoDir.mkdirs()
            Git.init().setDirectory(repoDir).call().close()
            refreshAfterGitOperation(repoDir)
        }
    }


    private suspend fun integration(
        repoDir: File,
        type: GitOperationType,
        needsIdentity: Boolean = false,
        block: (Git) -> GitIntegrationResult,
    ): GitIntegrationResult {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        return withContext(io) {
            operationCoordinator.runExclusive(repoDir, type, cancellable = true) {
                try {
                    ensureOperationActive(repoDir)
                    openGit(repoDir).use { git ->
                        if (needsIdentity) {
                            withTemporaryIdentity(git.repository, identityFor(git.repository)) { block(git) }
                        } else {
                            block(git)
                        }
                    }.also { ensureOperationActive(repoDir) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    throw JGitExceptionMapper.map(error)
                } finally {
                    withContext(NonCancellable) {
                        refreshQuietly(repoDir)
                        fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
                    }
                }
            }
        }
    }

    private suspend fun integrationUnit(repoDir: File, type: GitOperationType, block: (Git) -> Unit) {
        integration(repoDir, type) { git ->
            block(git)
            GitIntegrationResult(GitIntegrationStatus.APPLIED, git.repository.resolve(Constants.HEAD)?.name)
        }
    }

    private suspend fun resolveStage(repoDir: File, path: String, ours: Boolean) =
        integrationUnit(repoDir, GitOperationType.RESOLVE) { git ->
            val stage = if (ours) 2 else 3
            if (git.repository.readDirCache().hasStage(path, stage)) {
                git.checkout().setStage(if (ours) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS)
                    .addPath(path).call()
                git.add().addFilepattern(path).call()
            } else {
                File(repoDir, path).deleteRecursively()
                git.rm().setCached(true).addFilepattern(path).call()
            }
        }

    private suspend fun abortToOrigHead(repoDir: File, type: GitOperationType) =
        integrationUnit(repoDir, type) { git ->
            val target = git.repository.resolve(Constants.ORIG_HEAD) ?: git.repository.resolve(Constants.HEAD)
                ?: throw GitException.Unknown("Cannot find the pre-operation HEAD")
            git.reset().setRef(target.name).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call()
        }

    private suspend fun abortToHead(repoDir: File, type: GitOperationType) =
        integrationUnit(repoDir, type) { git ->
            git.reset().setRef(Constants.HEAD).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call()
        }

    private suspend fun runRebase(
        repoDir: File,
        operation: RebaseCommand.Operation,
        upstreamRef: String? = null,
    ): GitIntegrationResult = integration(repoDir, GitOperationType.REBASE, needsIdentity = true) { git ->
        val command = git.rebase().setOperation(operation).setProgressMonitor(operationProgressMonitor(repoDir))
        if (operation == RebaseCommand.Operation.BEGIN) {
            val upstream = git.repository.resolve(upstreamRef)
                ?: throw GitException.Unknown("Unknown upstream: $upstreamRef")
            command.setUpstream(upstream)
        }
        val result = command.call()
        when (result.status.name) {
            "OK" -> GitIntegrationResult(GitIntegrationStatus.MERGED, git.repository.resolve(Constants.HEAD)?.name)
            "FAST_FORWARD" -> GitIntegrationResult(GitIntegrationStatus.FAST_FORWARD, git.repository.resolve(Constants.HEAD)?.name)
            "UP_TO_DATE", "NOTHING_TO_COMMIT" -> GitIntegrationResult(GitIntegrationStatus.ALREADY_UP_TO_DATE)
            "STOPPED", "CONFLICTS", "STASH_APPLY_CONFLICTS" -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS, detail = result.status.name)
            "ABORTED" -> GitIntegrationResult(GitIntegrationStatus.ABORTED, git.repository.resolve(Constants.HEAD)?.name)
            else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, detail = result.status.name)
        }
    }

    private fun MergeResult.toIntegrationResult(): GitIntegrationResult = when (mergeStatus.name) {
        "FAST_FORWARD", "FAST_FORWARD_SQUASHED" -> GitIntegrationResult(GitIntegrationStatus.FAST_FORWARD, newHead?.name)
        "MERGED", "MERGED_NOT_COMMITTED", "MERGED_SQUASHED" -> GitIntegrationResult(GitIntegrationStatus.MERGED, newHead?.name)
        "ALREADY_UP_TO_DATE" -> GitIntegrationResult(GitIntegrationStatus.ALREADY_UP_TO_DATE, newHead?.name)
        "CONFLICTING", "CHECKOUT_CONFLICT" -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS, newHead?.name)
        "ABORTED" -> GitIntegrationResult(GitIntegrationStatus.ABORTED_FF_ONLY, newHead?.name)
        else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, newHead?.name, mergeStatus.name)
    }

    private fun DirCache.stageText(repo: Repository, path: String, stage: Int): String? {
        val first = findEntry(path).takeIf { it >= 0 } ?: return null
        val entry = (first until entryCount).asSequence().map(::getEntry)
            .takeWhile { it.pathString == path }.firstOrNull { it.stage == stage } ?: return null
        return repo.open(entry.objectId, Constants.OBJ_BLOB).bytes.toString(StandardCharsets.UTF_8)
    }

    private fun DirCache.hasStage(path: String, stage: Int): Boolean {
        val first = findEntry(path).takeIf { it >= 0 } ?: return false
        return (first until entryCount).asSequence().map(::getEntry)
            .takeWhile { it.pathString == path }.any { it.stage == stage }
    }

    private inline fun <T> withTemporaryIdentity(repo: Repository, identity: GitAuthorConfig, block: () -> T): T {
        val previousName = repo.config.getString("user", null, "name")
        val previousEmail = repo.config.getString("user", null, "email")
        repo.config.setString("user", null, "name", identity.name)
        repo.config.setString("user", null, "email", identity.email)
        return try {
            block()
        } finally {
            if (previousName == null) repo.config.unset("user", null, "name")
            else repo.config.setString("user", null, "name", previousName)
            if (previousEmail == null) repo.config.unset("user", null, "email")
            else repo.config.setString("user", null, "email", previousEmail)
        }
    }

    private suspend fun pushTags(repoDir: File, refSpec: RefSpec?, label: String): GitSyncResult = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.PUSH, cancellable = true) {
            var url: String? = null
            try {
                openGit(repoDir).use { git ->
                    val remote = Constants.DEFAULT_REMOTE_NAME
                    url = remoteUrl(git.repository, remote)
                    val command = git.push()
                        .setRemote(remote)
                        .setCredentialsProvider(credentialProviderFor(url, null))
                        .setProgressMonitor(operationProgressMonitor(repoDir))
                    if (refSpec == null) command.setPushTags() else command.setRefSpecs(refSpec)
                    val updates = command.call().flatMap { it.remoteUpdates }
                    ensureOperationActive(repoDir)
                    updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }?.let {
                        throw pushFailure(it, url, forceWithLease = false)
                    }
                    GitSyncResult(true, "Pushed $label")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                throwMappedOrCancelled(repoDir, error, url)
            } finally {
                refreshQuietly(repoDir)
            }
        }
    }

    private suspend fun applyStash(repoDir: File, index: Int, pop: Boolean) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        withContext(io) {
            operationCoordinator.runExclusive(repoDir, GitOperationType.STASH) {
                var attempted = false
                try {
                    openGit(repoDir).use { git ->
                        attempted = true
                        stashEngine.applyAndMaybeDrop(git, index, pop)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    throw JGitExceptionMapper.map(error)
                } finally {
                    withContext(NonCancellable) {
                        if (attempted) {
                            fileChangeBus.emitRootInvalidated(
                                repoDir.absolutePath,
                                RootInvalidationReason.GIT_OPERATION,
                            )
                        }
                        refreshQuietly(repoDir)
                    }
                }
            }
        }
    }

    private fun refreshRuntime(repoDir: File): RepoRefreshRuntime =
        refreshRuntimes.computeIfAbsent(key(repoDir)) { createRefreshRuntime(repoDir) }

    private fun createRefreshRuntime(repoDir: File): RepoRefreshRuntime {
        val canonicalRoot = runCatching { repoDir.canonicalFile }.getOrDefault(repoDir.absoluteFile)
        val initial = GitState(
            files = emptyList(),
            commitMessage = commitMessages[key(repoDir)].orEmpty(),
            isRepository = openRepositoryOrNull(repoDir)?.use { true } ?: false,
        )
        val state = MutableStateFlow(initial)
        val pipeline = GitRefreshPipeline(refreshScope) { includeIgnored, monitor ->
            val computed = statusComputer.compute(
                repoDir = canonicalRoot,
                includeIgnored = includeIgnored,
                commitMessage = commitMessages[key(repoDir)].orEmpty(),
                monitor = monitor,
            ).copy(commitMessage = commitMessages[key(repoDir)].orEmpty())
            if (state.value != computed) state.value = computed
        }
        val runtime = RepoRefreshRuntime(state, pipeline)
        refreshScope.launch {
            fileChangeBus.events
                .filterIsInstance<com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent.PathChanged>()
                .collect { event ->
                    if (event.path.isSameOrChildOf(canonicalRoot)) {
                        pipeline.requestDebounced(runtime.includeIgnored)
                    }
                }
        }
        return runtime
    }

    private suspend fun mutate(
        repoDir: File,
        type: GitOperationType,
        invalidatesRoot: Boolean = false,
        block: (Git) -> Unit,
    ) = withContext(io) {
        operationCoordinator.runExclusive(repoDir, type) {
            openGit(repoDir).use(block)
            refreshAfterGitOperation(repoDir)
            if (invalidatesRoot) {
                fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
            }
        }
    }

    private suspend fun configureRemote(
        repoDir: File,
        url: String? = null,
        block: (Git) -> Unit,
    ) = withContext(io) {
        operationCoordinator.runExclusive(repoDir, GitOperationType.CONFIGURE) {
            try {
                openGit(repoDir).use(block)
                refreshAfterGitOperation(repoDir)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                throw JGitExceptionMapper.map(error, url)
            }
        }
    }

    private suspend fun refreshAfterGitOperation(repoDir: File) {
        val runtime = refreshRuntime(repoDir)
        runtime.pipeline.requestImmediate(runtime.includeIgnored)
    }

    private suspend fun refreshQuietly(repoDir: File) {
        runCatching { refreshAfterGitOperation(repoDir) }
    }

    private suspend fun identityFor(repo: Repository): GitAuthorConfig =
        localIdentity(repo) ?: authorStore.get() ?: FALLBACK_AUTHOR

    private fun localIdentity(repo: Repository): GitAuthorConfig? {
        val localConfig = repo.localConfig()
        val name = localConfig.getString("user", null, "name")?.takeIf { it.isNotBlank() } ?: return null
        val email = localConfig.getString("user", null, "email")?.takeIf { it.isNotBlank() } ?: return null
        return GitAuthorConfig(name, email)
    }

    private fun Repository.localConfig(): FileBasedConfig {
        val file = (config as? FileBasedConfig)?.file ?: File(directory, "config")
        return FileBasedConfig(file, FS.DETECTED).apply { load() }
    }

    private fun trackingRemote(repo: Repository, branch: String): String =
        remoteEngine.trackingRemote(repo, branch)

    private fun upstreamFor(repo: Repository, branch: String): GitUpstream? =
        remoteEngine.upstreamFor(repo, branch)

    private fun currentLocalBranch(repo: Repository): String {
        val fullBranch = repo.fullBranch
        if (fullBranch?.startsWith(Constants.R_HEADS) != true) {
            throw GitException.Unknown("A local branch must be checked out for this operation")
        }
        return Repository.shortenRefName(fullBranch)
    }

    private fun operationProgressMonitor(repoDir: File): ProgressMonitor = object : EmptyProgressMonitor() {
        private var task = ""
        private var total = 0
        private var completed = 0

        override fun beginTask(title: String?, totalWork: Int) {
            task = title.orEmpty()
            total = totalWork
            completed = 0
            publish()
        }

        override fun update(completed: Int) {
            this.completed += completed
            publish()
        }

        override fun endTask() {
            if (total > 0) completed = total
            publish()
        }

        override fun isCancelled(): Boolean = operationCoordinator.isCancellationRequested(repoDir)

        private fun publish() {
            val fraction = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else null
            operationCoordinator.updateProgress(repoDir, fraction, task.ifBlank { "Synchronising" })
        }
    }

    private fun ensureOperationActive(repoDir: File) {
        if (operationCoordinator.isCancellationRequested(repoDir)) {
            throw CancellationException("Git operation cancelled")
        }
    }

    private fun throwMappedOrCancelled(repoDir: File, error: Throwable, url: String?): Nothing {
        if (operationCoordinator.isCancellationRequested(repoDir)) {
            val cancelled = CancellationException("Git operation cancelled")
            cancelled.initCause(error)
            throw cancelled
        }
        throw JGitExceptionMapper.map(error, url)
    }

    private fun pushFailure(
        update: RemoteRefUpdate,
        url: String?,
        forceWithLease: Boolean,
    ): GitException {
        val detail = GitUrlRedactor.redact(
            "Rejected: ${update.status} ${update.message.orEmpty()}".trim(),
            url,
        )
        return when {
            update.status == RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> GitException.StaleLease(detail)
            forceWithLease && (detail.contains("lease", ignoreCase = true) ||
                detail.contains("stale", ignoreCase = true)) -> GitException.StaleLease(detail)
            update.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> GitException.NonFastForward(detail)
            else -> GitException.Unknown(detail)
        }
    }

    private fun credentialProviderFor(url: String?, explicit: GitCredentials?): UsernamePasswordCredentialsProvider? {
        val credentials = explicit?.takeIf { it.token.isNotBlank() }
            ?: url?.let { credentialStore.credentialsForUrl(it) }
            ?: return null
        val username = credentials.username.ifBlank { DEFAULT_TOKEN_USERNAME }
        return UsernamePasswordCredentialsProvider(username, credentials.token)
    }

    private fun writeAndroidGitignore(repoDir: File) {
        val ignore = File(repoDir, ".gitignore")
        val existing = ignore.takeIf(File::exists)?.readLines().orEmpty()
        val missing = ANDROID_GITIGNORE_LINES.filterNot { entry -> existing.any { it.trim() == entry } }
        if (missing.isEmpty()) return
        ignore.parentFile?.mkdirs()
        val needsNewline = ignore.exists() && ignore.length() > 0L && !ignore.readText().endsWith('\n')
        ignore.appendText(buildString {
            if (needsNewline) append('\n')
            missing.forEach { append(it).append('\n') }
        })
    }

    private fun remoteUrl(repo: Repository, remote: String): String? =
        remoteEngine.remoteUrl(repo, remote)

    private fun isRepo(repoDir: File): Boolean = openRepositoryOrNull(repoDir)?.use { true } ?: false

    private fun openGit(repoDir: File): Git = Git(
        checkNotNull(openRepositoryOrNull(repoDir)) { "Not a Git repository: ${repoDir.absolutePath}" },
    )

    private fun String.isSameOrChildOf(root: File): Boolean {
        val path = runCatching { File(this).canonicalPath }.getOrDefault(File(this).absolutePath)
        return path == root.path || path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)
    }

    private fun key(repoDir: File): String = runCatching { repoDir.canonicalPath }.getOrDefault(repoDir.path)

    private fun RevCommit.toGitCommit(): GitCommit = GitCommit(
        id = name,
        shortId = abbreviate(7).name(),
        message = shortMessage,
        authorName = authorIdent.name,
        authorEmail = authorIdent.emailAddress,
        timeMillis = commitTime.toLong() * 1000L,
    )

    private class RepoRefreshRuntime(
        val state: MutableStateFlow<GitState>,
        val pipeline: GitRefreshPipeline,
        @Volatile var includeIgnored: Boolean = false,
    )

    private companion object {
        const val DEFAULT_AUTHOR_NAME = "Android Studio Lite"
        const val DEFAULT_AUTHOR_EMAIL = "asl@localhost"
        val FALLBACK_AUTHOR = GitAuthorConfig(DEFAULT_AUTHOR_NAME, DEFAULT_AUTHOR_EMAIL)
        const val DEFAULT_TOKEN_USERNAME = "x-access-token"
        val ANDROID_GITIGNORE_LINES = listOf(
            "build/",
            ".gradle/",
            "local.properties",
            ".idea/",
            "*.iml",
            ".kotlin/",
            "captures/",
            ".cxx/",
        )

        val ACCEPTED_PUSH_STATUSES = setOf(
            RemoteRefUpdate.Status.OK,
            RemoteRefUpdate.Status.UP_TO_DATE,
        )

        fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }

        fun String.toFullBranchRef(): String = if (startsWith(Constants.R_REFS)) this else Constants.R_HEADS + this
    }
}
