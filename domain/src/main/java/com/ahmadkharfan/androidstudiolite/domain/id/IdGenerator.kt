package com.ahmadkharfan.androidstudiolite.domain.id

fun interface IdGenerator {
    fun newId(): String
}

val UuidIdGenerator = IdGenerator { java.util.UUID.randomUUID().toString() }
