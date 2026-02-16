package io.github.fenrur.signal.impl

actual class CopyOnWriteArrayList<E> actual constructor() : AbstractMutableList<E>() {
    private val delegate = ArrayList<E>()

    actual override val size: Int get() = delegate.size

    actual override fun get(index: Int): E = delegate[index]

    actual override fun add(index: Int, element: E) = delegate.add(index, element)

    actual override fun removeAt(index: Int): E = delegate.removeAt(index)

    actual override fun set(index: Int, element: E): E = delegate.set(index, element)
}
