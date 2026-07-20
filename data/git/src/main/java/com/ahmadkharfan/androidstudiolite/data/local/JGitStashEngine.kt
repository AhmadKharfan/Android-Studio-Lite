package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit

internal class JGitStashEngine {
    fun create(git: Git, person: PersonIdent, message: String?, includeUntracked: Boolean): RevCommit? =
        git.stashCreate()
            .setIncludeUntracked(includeUntracked)
            .setPerson(person)
            .apply { message?.takeIf { it.isNotBlank() }?.let(::setWorkingDirectoryMessage) }
            .call()

    fun list(git: Git): List<GitStash> =
        git.stashList().call().mapIndexed { index, commit ->
            GitStash(index, commit.name, commit.shortMessage, commit.commitTime * 1_000L)
        }

    fun apply(git: Git, index: Int) {
        git.stashApply().setStashRef("stash@{$index}").call()
    }

    fun drop(git: Git, index: Int) {
        git.stashDrop().setStashRef(index).call()
    }

    fun applyAndMaybeDrop(git: Git, index: Int, pop: Boolean) {
        apply(git, index)
        if (pop) drop(git, index)
    }
}
