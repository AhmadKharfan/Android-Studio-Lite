package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials

/**
 * Secure, per-host store for HTTPS git credentials (personal access tokens). Keyed by remote host so
 * a token entered when cloning is reused for later fetch/push against the same host.
 */
interface GitCredentialStore {

    /** Credentials for the host of [url], or null if none saved. */
    fun credentialsForUrl(url: String): GitCredentials?

    /** Persist [credentials] for [host] (e.g. "github.com"), overwriting any existing entry. */
    fun save(host: String, credentials: GitCredentials)

    /** Remove any credentials saved for [host]. */
    fun clear(host: String)
}
