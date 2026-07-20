package com.ahmadkharfan.androidstudiolite.data.remote

class RemoteException(
    val httpStatus: Int,
    val code: String?,
    override val message: String,
) : Exception(message) {
    val isUnauthorized: Boolean get() = httpStatus == 401
}
