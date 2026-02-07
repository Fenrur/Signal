package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that emits pairs of consecutive values, with glitch-free semantics.
 *
 * Useful for detecting changes or computing deltas.
 *
 * @param T the type of source values
 * @param source the source signal
 */
class PairwiseSignal<T>(
    private val source: Signal<T>
) : AbstractComputedSignal<Pair<T, T>>() {

    override val sources: List<Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)
    private val previousValue: AtomicReference<T>

    override val cachedValue: AtomicReference<Pair<T, T>>

    init {
        val initial = source.value
        previousValue = AtomicReference(initial)
        cachedValue = AtomicReference(initial to initial)
        lastSourceVersion.set(getVersion(source))
    }

    override fun computeValue(): Pair<T, T> {
        val current = source.value
        val previous = previousValue.get()
        val pair = previous to current
        previousValue.set(current)
        return pair
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.get()

    override fun updateSourceVersions() {
        lastSourceVersion.set(getVersion(source))
    }

    override fun toString(): String = "PairwiseSignal(value=$value, isClosed=$isClosed)"
}
