package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitUpstream
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish

internal class JGitRemoteEngine {
    fun list(git: Git): List<GitRemote> =
        RemoteConfig.getAllRemoteConfigs(git.repository.config).map { remote ->
            GitRemote(
                name = remote.name,
                url = remote.urIs.firstOrNull()?.toString()?.let(GitUrlRedactor::stripUserInfo).orEmpty(),
                pushUrl = remote.pushURIs.firstOrNull()?.toString()?.let(GitUrlRedactor::stripUserInfo),
            )
        }.sortedBy { it.name }

    fun add(git: Git, name: String, url: String) {
        git.remoteAdd().setName(name).setUri(URIish(url)).call()
    }

    fun setUrl(git: Git, name: String, url: String) {
        git.remoteSetUrl().setRemoteName(name).setRemoteUri(URIish(url)).call()
    }

    fun remove(git: Git, name: String) {
        git.remoteRemove().setRemoteName(name).call()
    }

    fun setUpstream(repo: Repository, branch: String, remote: String, remoteBranch: String) {
        val config = repo.config
        config.setString("branch", branch, "remote", remote)
        config.setString("branch", branch, "merge", remoteBranch.toFullBranchRef())
        config.save()
    }

    fun upstreamOf(repo: Repository, branch: String): GitUpstream? {
        val config = BranchConfig(repo.config, branch)
        val remote = config.remote?.takeIf { it.isNotBlank() } ?: return null
        val merge = config.merge?.takeIf { it.isNotBlank() } ?: return null
        return GitUpstream(branch, remote, Repository.shortenRefName(merge))
    }

    fun trackingRemote(repo: Repository, branch: String): String =
        BranchConfig(repo.config, branch).remote?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_REMOTE_NAME

    fun upstreamFor(repo: Repository, branch: String): GitUpstream? = upstreamOf(repo, branch)

    fun remoteUrl(repo: Repository, remote: String): String? =
        repo.config.getString("remote", remote, "url")

    private fun String.toFullBranchRef(): String =
        if (startsWith(Constants.R_REFS)) this else Constants.R_HEADS + this
}
