package com.ahmadkharfan.androidstudiolite.domain.time

fun interface AslClock {
    fun nowMillis(): Long
}

val SystemAslClock = AslClock { System.currentTimeMillis() }
