package com.ahmadkharfan.androidstudiolite.domain.model

/**
 * A token GitHub issued at the end of a device-flow sign-in.
 *
 * OAuth apps issue tokens that don't expire (both expiries null). GitHub Apps issue user tokens that
 * expire, together with a refresh token that can renew them.
 */
data class GitHubTokenGrant(
    val accessToken: String,
    val expiresInSeconds: Long? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresInSeconds: Long? = null,
)
