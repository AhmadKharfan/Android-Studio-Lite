package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmoduleStatus
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.submodule.SubmoduleStatusType
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.eclipse.jgit.transport.CredentialsProvider

/** Pure JGit logic for inspecting and synchronising submodules. */
internal class JGitSubmoduleEngine {

    fun list(git: Git): List<GitSubmodule> {
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
        return statuses.map { (path, value) ->
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

    fun init(git: Git) {
        git.submoduleInit().call()
    }

    fun updateAll(
        git: Git,
        credentialsFor: (url: String?) -> CredentialsProvider?,
        monitorFor: () -> ProgressMonitor,
        ensureActive: () -> Unit,
    ) {
        val modules = git.submoduleStatus().call()
        val urls = mutableMapOf<String, String>()
        SubmoduleWalk.forIndex(git.repository).use { walk ->
            while (walk.next()) urls[walk.path] = walk.remoteUrl.orEmpty()
        }
        modules.forEach { (path, _) ->
            ensureActive()
            git.submoduleUpdate()
                .addPath(path)
                .setCredentialsProvider(credentialsFor(urls[path]))
                .setProgressMonitor(monitorFor())
                .call()
        }
    }
}
