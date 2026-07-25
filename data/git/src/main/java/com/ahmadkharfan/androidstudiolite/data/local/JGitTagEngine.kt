package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevWalk

/** Pure JGit logic for creating and deleting tags. */
internal class JGitTagEngine {

    fun create(git: Git, name: String, message: String?, targetCommit: String?, identity: GitAuthorConfig) {
        val command = git.tag().setName(name)
        if (message == null) {
            command.setAnnotated(false)
        } else {
            command.setAnnotated(true).setMessage(message).setTagger(PersonIdent(identity.name, identity.email))
        }
        targetCommit?.takeIf { it.isNotBlank() }?.let { target ->
            RevWalk(git.repository).use { walk ->
                val objectId = git.repository.resolve(target)
                    ?: throw GitException.Unknown("Unknown tag target: $target")
                command.setObjectId(walk.parseAny(objectId))
            }
        }
        command.call()
    }

    fun delete(git: Git, name: String) {
        git.tagDelete().setTags(name).call()
    }
}
