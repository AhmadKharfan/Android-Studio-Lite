package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.LockFailedException

internal object JGitExceptionMapper {
    private val authMarkers = listOf("auth", "not authorized", "unauthorized", "401", "403", "credential")

    fun map(error: Throwable, url: String? = null): GitException {
        if (error is GitException) return error
        val message = GitUrlRedactor.redact(error.message, url)
        val transport = error.findCause<TransportException>()
        return when {
            error.findCause<LockFailedException>() != null -> GitException.RepositoryLocked(message, error)
            transport != null && authMarkers.any { message.contains(it, ignoreCase = true) } ->
                GitException.Auth(message, error)
            transport != null -> GitException.Network(message, error)
            else -> GitException.Unknown(message, error)
        }
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}
