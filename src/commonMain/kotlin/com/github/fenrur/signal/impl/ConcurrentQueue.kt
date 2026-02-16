package com.github.fenrur.signal.impl

expect class ConcurrentQueue<E>() {
    fun offer(element: E): Boolean
    fun poll(): E?
    fun isNotEmpty(): Boolean
}
