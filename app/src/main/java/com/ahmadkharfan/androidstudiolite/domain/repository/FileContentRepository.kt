package com.ahmadkharfan.androidstudiolite.domain.repository
interface FileContentRepository {
    suspend fun readText(path: String): String
    suspend fun writeText(path: String, text: String)
}
