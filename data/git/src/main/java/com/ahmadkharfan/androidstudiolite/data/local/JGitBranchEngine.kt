package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException
import org.eclipse.jgit.api.errors.NotMergedException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository

internal class JGitBranchEngine {
    fun list(git: Git): List<GitBranch> {
        val current = git.repository.fullBranch
            ?.takeIf { it.startsWith(Constants.R_HEADS) }
            ?.let(Repository::shortenRefName)
        return git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
            .filterNot { it.name.startsWith(Constants.R_REMOTES) && it.name.endsWith("/HEAD") }
            .map { ref ->
                val shortName = Repository.shortenRefName(ref.name)
                GitBranch(
                    name = shortName,
                    isRemote = ref.name.startsWith(Constants.R_REMOTES),
                    current = shortName == current,
                )
            }
    }

    fun create(git: Git, name: String, checkout: Boolean) {
        git.branchCreate().setName(name).call()
        if (checkout) git.checkout().setName(name).call()
    }

    fun checkout(git: Git, name: String) {
        git.checkout().setName(name).call()
    }

    fun checkoutRemote(git: Git, remoteBranch: String, localName: String?) {
        val local = localName?.takeIf { it.isNotBlank() } ?: remoteBranch.substringAfter('/')
        git.branchCreate()
            .setName(local)
            .setStartPoint(remoteBranch)
            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
            .call()
        git.checkout().setName(local).call()
    }

    fun rename(git: Git, oldName: String, newName: String) {
        git.branchRename().setOldName(oldName).setNewName(newName).call()
    }

    fun delete(git: Git, name: String, force: Boolean) {
        try {
            git.branchDelete().setBranchNames(name).setForce(force).call()
        } catch (error: CannotDeleteCurrentBranchException) {
            throw GitException.CurrentBranch("Switch branches before deleting $name", error)
        } catch (error: NotMergedException) {
            throw GitException.BranchNotMerged("$name has unmerged commits; force deletion to continue", error)
        }
    }
}
