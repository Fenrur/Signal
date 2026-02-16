package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import kotlin.concurrent.atomics.*

/**
 * A [Signal] that transforms values and filters out nulls, with glitch-free semantics.
 *
 * When the transform returns null, the signal retains its previous non-null value.
 *
 * @param S the type of source values
 * @param R the type of transformed non-null values
 * @param source the source signal
 * @param transform transformation that may return null
 * @throws IllegalStateException if initial value transforms to null
 */
class MapNotNullSignal<S, R : Any>(
    private val source: Signal<S>,
    private val transform: (S) -> R?
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)

    override val cachedValue: AtomicReference<R>

    init {
        val initialTransformed = transform(source.value)
            ?: throw IllegalStateException("mapNotNull requires initial value to transform to non-null")
        cachedValue = AtomicReference(initialTransformed)
        lastSourceVersion.store(getVersion(source))
    }

    override fun computeValue(): R {
        val transformed = transform(source.value)
        return transformed ?: cachedValue.load()
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.load()

    override fun updateSourceVersions() {
        lastSourceVersion.store(getVersion(source))
    }

    override fun toString(): String = "MapNotNullSignal(value=$value, isClosed=$isClosed)"
}
