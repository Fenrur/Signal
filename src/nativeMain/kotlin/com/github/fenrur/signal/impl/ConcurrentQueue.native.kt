package com.github.fenrur.signal.impl

import kotlin.concurrent.atomics.*

actual class ConcurrentQueue<E> actual constructor() {
    private val ref = AtomicReference<List<E>>(emptyList())

    actual fun offer(element: E): Boolean {
        while (true) {
            val current = ref.load()
            val next = current + element
            if (ref.compareAndSet(current, next)) return true
        }
    }

    actual fun poll(): E? {
        while (true) {
            val current = ref.load()
            if (current.isEmpty()) return null
            val head = current.first()
            val next = current.subList(1, current.size)
            if (ref.compareAndSet(current, next)) return head
        }
    }

    actual fun isNotEmpty(): Boolean = ref.load().isNotEmpty()
}
