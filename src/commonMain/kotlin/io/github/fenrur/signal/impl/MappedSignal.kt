package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.Signal] that transforms values using a mapping function, with glitch-free semantics.
 *
 * @param S the type of source values
 * @param R the type of transformed values
 * @param source the source signal
 * @param transform the transformation function
 */
class MappedSignal<S, R>(
    private val source: Signal<S>,
    private val transform: (S) -> R
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)

    override val cachedValue: AtomicReference<R> = AtomicReference(transform(source.value))

    init {
        lastSourceVersion.store(getVersion(source))
    }

    override fun computeValue(): R = transform(source.value)

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.load()

    override fun updateSourceVersions() {
        lastSourceVersion.store(getVersion(source))
    }

    override fun toString(): String = "MappedSignal(value=$value, isClosed=$isClosed)"
}
