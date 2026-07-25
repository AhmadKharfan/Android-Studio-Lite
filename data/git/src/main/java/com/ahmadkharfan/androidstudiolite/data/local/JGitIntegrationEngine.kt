package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import java.io.File
import java.nio.charset.StandardCharsets

/** Pure JGit logic for the history-integrating operations, conflict resolution, and reset/clean. */
internal class JGitIntegrationEngine {

    fun merge(git: Git, ref: String, ffMode: GitFastForwardMode, message: String?, monitor: ProgressMonitor): GitIntegrationResult {
        val target = git.repository.resolve(ref) ?: throw GitException.Unknown("Unknown ref: $ref")
        val command = git.merge().include(target).setProgressMonitor(monitor).setFastForward(
            when (ffMode) {
                GitFastForwardMode.FF_ALLOWED -> MergeCommand.FastForwardMode.FF
                GitFastForwardMode.FF_ONLY -> MergeCommand.FastForwardMode.FF_ONLY
                GitFastForwardMode.NO_FF -> MergeCommand.FastForwardMode.NO_FF
            },
        )
        message?.takeIf { it.isNotBlank() }?.let(command::setMessage)
        return command.call().toIntegrationResult()
    }

    fun cherryPick(git: Git, commitId: String, monitor: ProgressMonitor): GitIntegrationResult {
        val id = git.repository.resolve(commitId) ?: throw GitException.Unknown("Unknown commit: $commitId")
        val result = git.cherryPick().include(id).setProgressMonitor(monitor).call()
        return when (result.status.name) {
            "OK" -> GitIntegrationResult(GitIntegrationStatus.APPLIED, result.newHead?.name)
            "CONFLICTING" -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS)
            else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, detail = result.status.name)
        }
    }

    fun revert(git: Git, commitId: String, monitor: ProgressMonitor): GitIntegrationResult {
        val id = git.repository.resolve(commitId) ?: throw GitException.Unknown("Unknown commit: $commitId")
        val command = git.revert().include(id).setProgressMonitor(monitor)
        val result = command.call()
        return when {
            result != null -> GitIntegrationResult(GitIntegrationStatus.APPLIED, result.name)
            !command.unmergedPaths.isNullOrEmpty() -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS)
            else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, detail = command.failingResult?.mergeStatus?.name)
        }
    }

    fun rebase(git: Git, operation: RebaseCommand.Operation, upstreamRef: String?, monitor: ProgressMonitor): GitIntegrationResult {
        val command = git.rebase().setOperation(operation).setProgressMonitor(monitor)
        if (operation == RebaseCommand.Operation.BEGIN) {
            val upstream = git.repository.resolve(upstreamRef)
                ?: throw GitException.Unknown("Unknown upstream: $upstreamRef")
            command.setUpstream(upstream)
        }
        val result = command.call()
        return when (result.status.name) {
            "OK" -> GitIntegrationResult(GitIntegrationStatus.MERGED, git.repository.resolve(Constants.HEAD)?.name)
            "FAST_FORWARD" -> GitIntegrationResult(GitIntegrationStatus.FAST_FORWARD, git.repository.resolve(Constants.HEAD)?.name)
            "UP_TO_DATE", "NOTHING_TO_COMMIT" -> GitIntegrationResult(GitIntegrationStatus.ALREADY_UP_TO_DATE)
            "STOPPED", "CONFLICTS", "STASH_APPLY_CONFLICTS" -> GitIntegrationResult(GitIntegrationStatus.CONFLICTS, detail = result.status.name)
            "ABORTED" -> GitIntegrationResult(GitIntegrationStatus.ABORTED, git.repository.resolve(Constants.HEAD)?.name)
            else -> GitIntegrationResult(GitIntegrationStatus.ABORTED, detail = result.status.name)
        }
    }

    fun conflictEntries(git: Git, repoDir: File): List<GitConflictEntry> {
        val cache = git.repository.readDirCache()
        return (0 until cache.entryCount).asSequence().map(cache::getEntry)
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

    fun resolveStage(git: Git, repoDir: File, path: String, ours: Boolean) {
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

    fun markResolved(git: Git, repoDir: File, path: String) {
        if (File(repoDir, path).exists()) git.add().addFilepattern(path).call()
        else git.rm().setCached(true).addFilepattern(path).call()
    }

    fun restoreFiles(git: Git, paths: List<String>) {
        git.checkout().setStartPoint(Constants.HEAD).addPaths(paths).call()
    }

    fun reset(git: Git, commitId: String, mode: GitResetMode) {
        git.reset().setRef(commitId).setMode(
            when (mode) {
                GitResetMode.SOFT -> ResetCommand.ResetType.SOFT
                GitResetMode.MIXED -> ResetCommand.ResetType.MIXED
                GitResetMode.HARD -> ResetCommand.ResetType.HARD
            },
        ).call()
    }

    fun abortToHead(git: Git) {
        git.reset().setRef(Constants.HEAD).setMode(ResetCommand.ResetType.HARD).call()
    }

    fun abortToOrigHead(git: Git) {
        val target = git.repository.resolve(Constants.ORIG_HEAD) ?: git.repository.resolve(Constants.HEAD)
            ?: throw GitException.Unknown("Cannot find the pre-operation HEAD")
        git.reset().setRef(target.name).setMode(ResetCommand.ResetType.HARD).call()
    }

    fun clean(git: Git, dryRun: Boolean, includeIgnored: Boolean): List<String> =
        git.clean().setDryRun(dryRun).setCleanDirectories(true).setIgnore(!includeIgnored).call()
            .map { it.replace('\\', '/') }.sorted()

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
}
