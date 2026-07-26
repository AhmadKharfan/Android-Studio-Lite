package com.ahmadkharfan.androidstudiolite.domain.time

fun interface MonotonicClock {
    fun elapsedMillis(): Long
}

val SystemMonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000 }
