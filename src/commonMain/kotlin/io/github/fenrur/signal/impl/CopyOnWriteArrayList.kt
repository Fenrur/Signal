package io.github.fenrur.signal.impl

expect class CopyOnWriteArrayList<E>() : MutableList<E> {
    override val size: Int
    override fun get(index: Int): E
    override fun set(index: Int, element: E): E
    override fun add(element: E): Boolean
    override fun add(index: Int, element: E)
    override fun remove(element: E): Boolean
    override fun removeAt(index: Int): E
    override fun addAll(elements: Collection<E>): Boolean
    override fun addAll(index: Int, elements: Collection<E>): Boolean
    override fun removeAll(elements: Collection<E>): Boolean
    override fun retainAll(elements: Collection<E>): Boolean
    override fun clear()
    override fun isEmpty(): Boolean
    override fun contains(element: E): Boolean
    override fun containsAll(elements: Collection<E>): Boolean
    override fun indexOf(element: E): Int
    override fun lastIndexOf(element: E): Int
    override fun iterator(): MutableIterator<E>
    override fun listIterator(): MutableListIterator<E>
    override fun listIterator(index: Int): MutableListIterator<E>
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E>
}
