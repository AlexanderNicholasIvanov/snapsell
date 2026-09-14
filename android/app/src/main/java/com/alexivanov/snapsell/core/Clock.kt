package com.alexivanov.snapsell.core

/** Wall-clock provider; swap for a fixed clock in tests. */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
