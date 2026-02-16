package com.github.fenrur.signal.impl

actual class ConcurrentQueue<E> actual constructor() {
    private val delegate = ArrayDeque<E>()

    actual fun offer(element: E): Boolean {
        delegate.addLast(element)
        return true
    }

    actual fun poll(): E? = delegate.removeFirstOrNull()

    actual fun isNotEmpty(): Boolean = delegate.isNotEmpty()
}
