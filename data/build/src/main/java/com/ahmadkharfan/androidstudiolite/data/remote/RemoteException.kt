package com.ahmadkharfan.androidstudiolite.data.remote

/**
 * Raised when a control-plane call fails with a structured error envelope or an unexpected HTTP
 * status. [code] is the server's error code (e.g. `QUOTA_EXCEEDED`, `INVALID_TOKEN`) when present.
 */
class RemoteException(
    val httpStatus: Int,
    val code: String?,
    override val message: String,
) : Exception(message) {
    /** A 401 means the cached device token is stale/revoked and should be re-minted. */
    val isUnauthorized: Boolean get() = httpStatus == 401
}
