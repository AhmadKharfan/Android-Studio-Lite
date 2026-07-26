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
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.FS
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class JGitGitRepository internal constructor(
    private val credentialStore: GitCredentialStore,
    private val io: CoroutineDispatcher,
    private val operationCoordinator: GitOperationCoordinator,
    private val fileChangeBus: FileChangeBus,
    private val authorStore: GitAuthorStore,
    private val workspaceWriteGate: WorkspaceWriteGate,
    private val statusComputer: JGitStatusComputer,
    private val diffReader: JGitDiffReader,
    private val indexEditor: JGitIndexEditor,
    private val historyEngine: JGitHistoryEngine,
    private val stashEngine: JGitStashEngine,
    private val branchEngine: JGitBranchEngine,
    private val remoteEngine: JGitRemoteEngine,
    private val integrationEngine: JGitIntegrationEngine,
    private val tagEngine: JGitTagEngine,
    private val submoduleEngine: JGitSubmoduleEngine,
) : GitRepository {

    private constructor(
        credentialStore: GitCredentialStore,
        io: CoroutineDispatcher,
        operationCoordinator: GitOperationCoordinator,
        fileChangeBus: FileChangeBus,
        authorStore: GitAuthorStore,
        workspaceWriteGate: WorkspaceWriteGate,
        diffReader: JGitDiffReader,
    ) : this(
        credentialStore,
        io,
        operationCoordinator,
        fileChangeBus,
        authorStore,
        workspaceWriteGate,
        JGitStatusComputer(),
        diffReader,
        JGitIndexEditor(diffReader),
        JGitHistoryEngine(),
        JGitStashEngine(),
        JGitBranchEngine(),
        JGitRemoteEngine(),
        JGitIntegrationEngine(),
        JGitTagEngine(),
        JGitSubmoduleEngine(),
    )

    constructor(
        credentialStore: GitCredentialStore,
        io: CoroutineDispatcher = Dispatchers.IO,
        operationCoordinator: GitOperationCoordinator = GitOperationCoordinator(),
        fileChangeBus: FileChangeBus = FileChangeBus(),
        authorStore: GitAuthorStore = InMemoryGitAuthorStore(),
        workspaceWriteGate: WorkspaceWriteGate = DefaultWorkspaceWriteGate(),
    ) : this(
        credentialStore,
        io,
        operationCoordinator,
        fileChangeBus,
        authorStore,
        workspaceWriteGate,
        JGitDiffReader(),
    )

    private val syncEngine = JGitSyncEngine(remoteEngine)
    private val commitMessages = ConcurrentHashMap<String, String>()
    private val refreshRuntimes = ConcurrentHashMap<String, RepoRefreshRuntime>()
    private val refreshScope = CoroutineScope(SupervisorJob() + io)

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
        withContext(io) { openGit(repoDir).use { diffReader.indexToWorktree(it.repository, path, force) } }

    override suspend fun diffHeadToIndex(repoDir: File, path: String, force: Boolean): GitFileDiff =
        withContext(io) { openGit(repoDir).use { diffReader.headToIndex(it.repository, path, force) } }

    override suspend fun diffCommitToParent(
        repoDir: File,
        commitId: String,
        path: String,
        force: Boolean,
    ): GitFileDiff = withContext(io) {
        openGit(repoDir).use { diffReader.commitToParent(it.repository, commitId, path, force) }
    }

    override suspend fun diffIndexToBuffer(repoDir: File, path: String, buffer: String): GitFileDiff =
        withContext(io) { openGit(repoDir).use { diffReader.indexToBuffer(it.repository, path, buffer) } }

    override suspend fun stageHunk(repoDir: File, path: String, hunk: GitDiffHunk) =
        mutate(repoDir, GitOperationType.PARTIAL_STAGE) { git ->
            indexEditor.updateIndex(git.repository, path, hunk, reverse = false)
        }

    override suspend fun unstageHunk(repoDir: File, path: String, hunk: GitDiffHunk) =
        mutate(repoDir, GitOperationType.PARTIAL_STAGE) { git ->
            indexEditor.updateIndex(git.repository, path, hunk, reverse = true)
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
        syncOperation(repoDir, GitOperationType.PUSH) { onUrl ->
            openGit(repoDir).use { git ->
                syncEngine.publishBranch(
                    git, name,
                    credentialsFor = { credentialProviderFor(it, null) },
                    monitor = operationProgressMonitor(repoDir),
                    onUrl = onUrl,
                    ensureActive = { ensureOperationActive(repoDir) },
                )
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
        syncOperation(repoDir, GitOperationType.DEEPEN) { onUrl ->
            openGit(repoDir).use { git ->
                syncEngine.deepen(
                    git,
                    credentialsFor = { credentialProviderFor(it, null) },
                    monitor = operationProgressMonitor(repoDir),
                    onUrl = onUrl,
                    ensureActive = { ensureOperationActive(repoDir) },
                )
            }
        }
    }

    override suspend fun listTags(repoDir: File): List<GitTag> = withContext(io) {
        openGit(repoDir).use { historyEngine.listTags(it.repository) }
    }

    override suspend fun createTag(repoDir: File, name: String, message: String?, targetCommit: String?) {
        val identity = withContext(io) { openGit(repoDir).use { identityFor(it.repository) } }
        mutate(repoDir, GitOperationType.TAG) { git ->
            tagEngine.create(git, name, message, targetCommit, identity)
        }
    }

    override suspend fun deleteTag(repoDir: File, name: String) = mutate(repoDir, GitOperationType.TAG) { git ->
        tagEngine.delete(git, name)
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
        integrationEngine.merge(git, ref, ffMode, message, operationProgressMonitor(repoDir))
    }

    override suspend fun mergeAbort(repoDir: File) = abortToOrigHead(repoDir, GitOperationType.MERGE)

    override suspend fun cherryPick(repoDir: File, commitId: String): GitIntegrationResult =
        integration(repoDir, GitOperationType.CHERRY_PICK, needsIdentity = true) { git ->
            integrationEngine.cherryPick(git, commitId, operationProgressMonitor(repoDir))
        }

    override suspend fun cherryPickAbort(repoDir: File) = abortToHead(repoDir, GitOperationType.CHERRY_PICK)

    override suspend fun revert(repoDir: File, commitId: String): GitIntegrationResult =
        integration(repoDir, GitOperationType.REVERT, needsIdentity = true) { git ->
            integrationEngine.revert(git, commitId, operationProgressMonitor(repoDir))
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
        openGit(repoDir).use { integrationEngine.conflictEntries(it, repoDir) }
    }

    override suspend fun resolveAcceptOurs(repoDir: File, path: String) = resolveStage(repoDir, path, ours = true)

    override suspend fun resolveAcceptTheirs(repoDir: File, path: String) = resolveStage(repoDir, path, ours = false)

    override suspend fun markResolved(repoDir: File, path: String) = integrationUnit(repoDir, GitOperationType.RESOLVE) { git ->
        integrationEngine.markResolved(git, repoDir, path)
    }

    override suspend fun restoreFiles(repoDir: File, paths: List<String>) {
        if (paths.isEmpty()) return
        integrationUnit(repoDir, GitOperationType.RESTORE) { git ->
            integrationEngine.restoreFiles(git, paths)
        }
    }

    override suspend fun reset(repoDir: File, commitId: String, mode: GitResetMode) =
        integrationUnit(repoDir, GitOperationType.RESET) { git ->
            integrationEngine.reset(git, commitId, mode)
        }

    override suspend fun clean(repoDir: File, dryRun: Boolean, includeIgnored: Boolean): List<String> =
        withContext(io) {
            operationCoordinator.runExclusive(repoDir, GitOperationType.CLEAN) {
                if (!dryRun) workspaceWriteGate.prepareForWorktreeMutation(repoDir)
                val removed = openGit(repoDir).use { integrationEngine.clean(it, dryRun, includeIgnored) }
                if (!dryRun) {
                    refreshAfterGitOperation(repoDir)
                    fileChangeBus.emitRootInvalidated(repoDir.absolutePath, RootInvalidationReason.GIT_OPERATION)
                }
                removed
            }
        }

    override suspend fun submodules(repoDir: File): List<GitSubmodule> = withContext(io) {
        openGit(repoDir).use { git -> submoduleEngine.list(git) }
    }

    override suspend fun submoduleInit(repoDir: File) = mutate(repoDir, GitOperationType.SUBMODULE) { git ->
        submoduleEngine.init(git)
    }

    override suspend fun submoduleUpdate(repoDir: File) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        withContext(io) {
            operationCoordinator.runExclusive(repoDir, GitOperationType.SUBMODULE, cancellable = true) {
                try {
                    openGit(repoDir).use { git ->
                        submoduleEngine.updateAll(
                            git,
                            credentialsFor = { url -> credentialProviderFor(url, null) },
                            monitorFor = { operationProgressMonitor(repoDir) },
                            ensureActive = { ensureOperationActive(repoDir) },
                        )
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
        syncOperation(repoDir, GitOperationType.FETCH) { onUrl ->
            openGit(repoDir).use { git ->
                syncEngine.fetch(
                    git, remote, prune,
                    credentialsFor = { credentialProviderFor(it, null) },
                    monitor = operationProgressMonitor(repoDir),
                    onUrl = onUrl,
                    ensureActive = { ensureOperationActive(repoDir) },
                )
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
    ): GitSyncResult = syncOperation(repoDir, GitOperationType.PUSH) { onUrl ->
        openGit(repoDir).use { git ->
            syncEngine.push(
                git, setUpstreamIfMissing, forceWithLease,
                credentialsFor = { credentialProviderFor(it, null) },
                monitor = operationProgressMonitor(repoDir),
                onUrl = onUrl,
                ensureActive = { ensureOperationActive(repoDir) },
            )
        }
    }

    override suspend fun pull(repoDir: File, mode: PullMode): GitSyncResult = withContext(io) {
        workspaceWriteGate.prepareForWorktreeMutation(repoDir)
        operationCoordinator.runExclusive(repoDir, GitOperationType.PULL, cancellable = true) {
            var url: String? = null
            var attempted = false
            try {
                openGit(repoDir).use { git ->
                    syncEngine.pull(
                        git, mode,
                        credentialsFor = { credentialProviderFor(it, null) },
                        monitor = operationProgressMonitor(repoDir),
                        onUrl = { url = it; attempted = true },
                        ensureActive = { ensureOperationActive(repoDir) },
                    )
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
            integrationEngine.resolveStage(git, repoDir, path, ours)
        }

    private suspend fun abortToOrigHead(repoDir: File, type: GitOperationType) =
        integrationUnit(repoDir, type) { git -> integrationEngine.abortToOrigHead(git) }

    private suspend fun abortToHead(repoDir: File, type: GitOperationType) =
        integrationUnit(repoDir, type) { git -> integrationEngine.abortToHead(git) }

    private suspend fun runRebase(
        repoDir: File,
        operation: RebaseCommand.Operation,
        upstreamRef: String? = null,
    ): GitIntegrationResult = integration(repoDir, GitOperationType.REBASE, needsIdentity = true) { git ->
        integrationEngine.rebase(git, operation, upstreamRef, operationProgressMonitor(repoDir))
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
        syncOperation(repoDir, GitOperationType.PUSH) { onUrl ->
            openGit(repoDir).use { git ->
                syncEngine.pushTags(
                    git, refSpec, label,
                    credentialsFor = { credentialProviderFor(it, null) },
                    monitor = operationProgressMonitor(repoDir),
                    onUrl = onUrl,
                    ensureActive = { ensureOperationActive(repoDir) },
                )
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

    private suspend fun <T> syncOperation(
        repoDir: File,
        type: GitOperationType,
        block: suspend (onUrl: (String?) -> Unit) -> T,
    ): T = operationCoordinator.runExclusive(repoDir, type, cancellable = true) {
        var url: String? = null
        try {
            block { url = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throwMappedOrCancelled(repoDir, error, url)
        } finally {
            refreshQuietly(repoDir)
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

        fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }

        fun String.toFullBranchRef(): String = if (startsWith(Constants.R_REFS)) this else Constants.R_HEADS + this
    }
}
