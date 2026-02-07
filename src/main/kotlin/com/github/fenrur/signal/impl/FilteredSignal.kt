package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that filters values based on a predicate, with glitch-free semantics.
 *
 * When the predicate returns false, the signal retains its previous matching value.
 *
 * @param T the type of values
 * @param source the source signal
 * @param predicate filter condition
 */
class FilteredSignal<T>(
    private val source: Signal<T>,
    private val predicate: (T) -> Boolean
) : AbstractComputedSignal<T>() {

    override val sources: List<Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)

    override val cachedValue: AtomicReference<T> = AtomicReference(source.value)

    init {
        lastSourceVersion.set(getVersion(source))
    }

    override fun computeValue(): T {
        val current = source.value
        return if (predicate(current)) current else cachedValue.get()
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.get()

    override fun updateSourceVersions() {
        lastSourceVersion.set(getVersion(source))
    }

    override fun toString(): String = "FilteredSignal(value=$value, isClosed=$isClosed)"
}
