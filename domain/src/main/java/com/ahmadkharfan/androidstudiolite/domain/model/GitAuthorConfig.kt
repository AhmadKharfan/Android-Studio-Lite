package com.ahmadkharfan.androidstudiolite.domain.model

data class GitAuthorConfig(val name: String, val email: String)

data class GitAuthorConfigState(
    val effective: GitAuthorConfig,
    val local: GitAuthorConfig?,
    val appGlobal: GitAuthorConfig?,
)
