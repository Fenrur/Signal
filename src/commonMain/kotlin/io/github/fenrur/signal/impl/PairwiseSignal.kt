package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.Signal] that emits pairs of consecutive values, with glitch-free semantics.
 *
 * Useful for detecting changes or computing deltas.
 *
 * @param T the type of source values
 * @param source the source signal
 */
class PairwiseSignal<T>(
    private val source: io.github.fenrur.signal.Signal<T>
) : io.github.fenrur.signal.impl.AbstractComputedSignal<Pair<T, T>>() {

    override val sources: List<io.github.fenrur.signal.Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)
    private val previousValue: AtomicReference<T>

    override val cachedValue: AtomicReference<Pair<T, T>>

    init {
        val initial = source.value
        previousValue = AtomicReference(initial)
        cachedValue = AtomicReference(initial to initial)
        lastSourceVersion.store(getVersion(source))
    }

    override fun computeValue(): Pair<T, T> {
        val current = source.value
        val previous = previousValue.exchange(current)
        return previous to current
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.load()

    override fun updateSourceVersions() {
        lastSourceVersion.store(getVersion(source))
    }

    override fun toString(): String = "PairwiseSignal(value=$value, isClosed=$isClosed)"
}
