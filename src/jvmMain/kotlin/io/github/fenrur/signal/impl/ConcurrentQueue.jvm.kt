package io.github.fenrur.signal.impl

import java.util.concurrent.ConcurrentLinkedQueue

actual class ConcurrentQueue<E> actual constructor() {
    private val delegate = ConcurrentLinkedQueue<E>()

    actual fun offer(element: E): Boolean = delegate.offer(element)
    actual fun poll(): E? = delegate.poll()
    actual fun isNotEmpty(): Boolean = delegate.isNotEmpty()
}
