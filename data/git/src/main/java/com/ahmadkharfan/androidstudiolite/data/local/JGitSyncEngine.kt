package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefLeaseSpec
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate

/**
 * Pure JGit logic for the remote-transport operations. The surrounding locking, progress
 * plumbing, cancellation and error mapping stay with the repository; this engine builds and
 * runs the fetch/push/pull commands and maps their results to the domain model.
 */
internal class JGitSyncEngine(private val remoteEngine: JGitRemoteEngine) {

    fun fetch(
        git: Git,
        remote: String?,
        prune: Boolean,
        credentialsFor: (String?) -> CredentialsProvider?,
        monitor: ProgressMonitor,
        onUrl: (String?) -> Unit,
        ensureActive: () -> Unit,
    ): GitSyncResult {
        val repo = git.repository
        val selectedRemote = remote ?: remoteEngine.trackingRemote(repo, repo.branch)
        val url = remoteEngine.remoteUrl(repo, selectedRemote)
        onUrl(url)
        val result = git.fetch()
            .setRemote(selectedRemote)
            .setRemoveDeletedRefs(prune)
            .setCredentialsProvider(credentialsFor(url))
            .setProgressMonitor(monitor)
            .call()
        ensureActive()
        return GitSyncResult(true, "Fetched ${result.trackingRefUpdates.size} ref update(s)")
    }

    fun push(
        git: Git,
        setUpstreamIfMissing: Boolean,
        forceWithLease: Boolean,
        credentialsFor: (String?) -> CredentialsProvider?,
        monitor: ProgressMonitor,
        onUrl: (String?) -> Unit,
        ensureActive: () -> Unit,
    ): GitSyncResult {
        val repo = git.repository
        val branch = currentLocalBranch(repo)
        val upstream = remoteEngine.upstreamFor(repo, branch)
        val remote = upstream?.remote ?: Constants.DEFAULT_REMOTE_NAME
        val remoteBranch = upstream?.remoteBranch ?: branch
        val remoteRef = "${Constants.R_HEADS}$remoteBranch"
        val url = remoteEngine.remoteUrl(repo, remote)
        onUrl(url)
        val command = git.push()
            .setRemote(remote)
            .setRefSpecs(RefSpec("${Constants.R_HEADS}$branch:$remoteRef"))
            .setCredentialsProvider(credentialsFor(url))
            .setProgressMonitor(monitor)
        if (forceWithLease) {
            val trackingRef = repo.findRef("${Constants.R_REMOTES}$remote/$remoteBranch")
                ?: throw GitException.StaleLease("No remote-tracking value for $remote/$remoteBranch; fetch first")
            command
                .setForce(true)
                .setRefLeaseSpecs(RefLeaseSpec(remoteRef, trackingRef.objectId.name))
        }
        val updates = command.call().flatMap { it.remoteUpdates }
        ensureActive()
        val rejected = updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }
        if (rejected != null) throw pushFailure(rejected, url, forceWithLease)
        if (upstream == null && setUpstreamIfMissing) {
            val config = repo.config
            config.setString("branch", branch, "remote", remote)
            config.setString("branch", branch, "merge", remoteRef)
            config.save()
        }
        return GitSyncResult(true, "Pushed ${updates.size} ref(s)")
    }

    fun publishBranch(
        git: Git,
        name: String,
        credentialsFor: (String?) -> CredentialsProvider?,
        monitor: ProgressMonitor,
        onUrl: (String?) -> Unit,
        ensureActive: () -> Unit,
    ): GitSyncResult {
        val repo = git.repository
        val upstream = remoteEngine.upstreamFor(repo, name)
        val remote = upstream?.remote ?: Constants.DEFAULT_REMOTE_NAME
        val remoteBranch = upstream?.remoteBranch ?: name
        val remoteRef = "${Constants.R_HEADS}$remoteBranch"
        val url = remoteEngine.remoteUrl(repo, remote)
        onUrl(url)
        val updates = git.push()
            .setRemote(remote)
            .setRefSpecs(RefSpec("${Constants.R_HEADS}$name:$remoteRef"))
            .setCredentialsProvider(credentialsFor(url))
            .setProgressMonitor(monitor)
            .call().flatMap { it.remoteUpdates }
        ensureActive()
        updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }?.let {
            throw pushFailure(it, url, forceWithLease = false)
        }
        if (upstream == null) {
            repo.config.setString("branch", name, "remote", remote)
            repo.config.setString("branch", name, "merge", remoteRef)
            repo.config.save()
        }
        return GitSyncResult(true, "Published $name")
    }

    fun deepen(
        git: Git,
        credentialsFor: (String?) -> CredentialsProvider?,
        monitor: ProgressMonitor,
        onUrl: (String?) -> Unit,
        ensureActive: () -> Unit,
    ): GitSyncResult {
        if (git.repository.objectDatabase.shallowCommits.isEmpty()) {
            return GitSyncResult(true, "History is already complete")
        }
        val remote = remoteEngine.trackingRemote(git.repository, git.repository.branch)
        val url = remoteEngine.remoteUrl(git.repository, remote)
        onUrl(url)
        val fetched = git.fetch()
            .setRemote(remote)
            .setUnshallow(true)
            .setCredentialsProvider(credentialsFor(url))
            .setProgressMonitor(monitor)
            .call()
        ensureActive()
        return GitSyncResult(true, "Deepened history (${fetched.trackingRefUpdates.size} ref update(s))")
    }

    fun pushTags(
        git: Git,
        refSpec: RefSpec?,
        label: String,
        credentialsFor: (String?) -> CredentialsProvider?,
        monitor: ProgressMonitor,
        onUrl: (String?) -> Unit,
        ensureActive: () -> Unit,
    ): GitSyncResult {
        val remote = Constants.DEFAULT_REMOTE_NAME
        val url = remoteEngine.remoteUrl(git.repository, remote)
        onUrl(url)
        val command = git.push()
            .setRemote(remote)
            .setCredentialsProvider(credentialsFor(url))
            .setProgressMonitor(monitor)
        if (refSpec == null) command.setPushTags() else command.setRefSpecs(refSpec)
        val updates = command.call().flatMap { it.remoteUpdates }
        ensureActive()
        updates.firstOrNull { it.status !in ACCEPTED_PUSH_STATUSES }?.let {
            throw pushFailure(it, url, forceWithLease = false)
        }
        return GitSyncResult(true, "Pushed $label")
    }

    fun pull(
        git: Git,
        mode: PullMode,
        credentialsFor: (String?) -> CredentialsProvider?,
        monitor: ProgressMonitor,
        onUrl: (String?) -> Unit,
        ensureActive: () -> Unit,
    ): GitSyncResult {
        val repo = git.repository
        val branch = currentLocalBranch(repo)
        val upstream = remoteEngine.upstreamFor(repo, branch)
        val remote = upstream?.remote ?: Constants.DEFAULT_REMOTE_NAME
        val remoteBranch = upstream?.remoteBranch ?: branch
        val url = remoteEngine.remoteUrl(repo, remote)
        onUrl(url)
        val result = git.pull()
            .setRemote(remote)
            .setRemoteBranchName(remoteBranch)
            .setRebase(mode == PullMode.REBASE)
            .setCredentialsProvider(credentialsFor(url))
            .setProgressMonitor(monitor)
            .call()
        ensureActive()
        return pullResultToSyncResult(result)
    }

    private fun currentLocalBranch(repo: Repository): String {
        val fullBranch = repo.fullBranch
        if (fullBranch?.startsWith(Constants.R_HEADS) != true) {
            throw GitException.Unknown("A local branch must be checked out for this operation")
        }
        return Repository.shortenRefName(fullBranch)
    }

    private fun pushFailure(update: RemoteRefUpdate, url: String?, forceWithLease: Boolean): GitException {
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

    private fun pullResultToSyncResult(result: PullResult): GitSyncResult {
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

    private companion object {
        val ACCEPTED_PUSH_STATUSES = setOf(
            RemoteRefUpdate.Status.OK,
            RemoteRefUpdate.Status.UP_TO_DATE,
        )
    }
}
