package io.github.fenrur.signal.impl

import kotlin.concurrent.atomics.*

actual class CopyOnWriteArrayList<E> actual constructor() : AbstractMutableList<E>() {
    private val ref = AtomicReference<List<E>>(emptyList())

    actual override val size: Int get() = ref.load().size

    actual override fun get(index: Int): E = ref.load()[index]

    actual override fun add(index: Int, element: E) {
        while (true) {
            val current = ref.load()
            val next = ArrayList(current).apply { add(index, element) }
            if (ref.compareAndSet(current, next)) return
        }
    }

    actual override fun removeAt(index: Int): E {
        while (true) {
            val current = ref.load()
            val removed = current[index]
            val next = ArrayList(current).apply { removeAt(index) }
            if (ref.compareAndSet(current, next)) return removed
        }
    }

    actual override fun set(index: Int, element: E): E {
        while (true) {
            val current = ref.load()
            val old = current[index]
            val next = ArrayList(current).apply { set(index, element) }
            if (ref.compareAndSet(current, next)) return old
        }
    }

    actual override fun add(element: E): Boolean {
        while (true) {
            val current = ref.load()
            val next = ArrayList(current).apply { add(element) }
            if (ref.compareAndSet(current, next)) return true
        }
    }

    actual override fun remove(element: E): Boolean {
        while (true) {
            val current = ref.load()
            if (element !in current) return false
            val next = ArrayList(current).apply { remove(element) }
            if (ref.compareAndSet(current, next)) return true
        }
    }

    actual override fun clear() {
        ref.store(emptyList())
    }

    actual override fun isEmpty(): Boolean = ref.load().isEmpty()

    actual override fun iterator(): MutableIterator<E> {
        // Return a snapshot iterator (like java CopyOnWriteArrayList)
        val snapshot = ref.load()
        return object : MutableIterator<E> {
            private val inner = snapshot.iterator()
            override fun hasNext(): Boolean = inner.hasNext()
            override fun next(): E = inner.next()
            override fun remove() {
                throw UnsupportedOperationException("CopyOnWriteArrayList iterator does not support remove")
            }
        }
    }
}
