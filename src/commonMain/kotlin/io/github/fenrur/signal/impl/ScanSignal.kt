package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.Signal] that accumulates values over time, with glitch-free semantics.
 *
 * Similar to Kotlin's `scan` or `runningFold`. Each emission includes
 * the accumulated result of applying the accumulator to all previous values.
 *
 * **Concurrency caveat:** Under concurrent access, if two threads read `.value` on a DIRTY signal
 * simultaneously, one accumulation step may apply against a stale base value. This is inherent
 * to the lock-free design and does not constitute a bug -- callers requiring strict serialisation
 * of accumulation steps should synchronise externally.
 *
 * @param T the type of source values
 * @param R the type of accumulated values
 * @param source the source signal
 * @param initial the initial accumulator value
 * @param accumulator function combining accumulator with each new value
 */
class ScanSignal<T, R>(
    private val source: io.github.fenrur.signal.Signal<T>,
    initial: R,
    private val accumulator: (acc: R, value: T) -> R
) : io.github.fenrur.signal.impl.AbstractComputedSignal<R>() {

    override val sources: List<io.github.fenrur.signal.Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)
    private val lastSourceValue: AtomicReference<T>

    override val cachedValue: AtomicReference<R>

    init {
        val initialSourceValue = source.value
        cachedValue = AtomicReference(accumulator(initial, initialSourceValue))
        lastSourceValue = AtomicReference(initialSourceValue)
        lastSourceVersion.store(getVersion(source))
    }

    override fun computeValue(): R {
        val current = source.value
        val lastValue = lastSourceValue.exchange(current)
        return if (current != lastValue) {
            accumulator(cachedValue.load(), current)
        } else {
            cachedValue.load()
        }
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.load()

    override fun updateSourceVersions() {
        lastSourceVersion.store(getVersion(source))
    }

    override fun toString(): String = "ScanSignal(value=$value, isClosed=$isClosed)"
}
