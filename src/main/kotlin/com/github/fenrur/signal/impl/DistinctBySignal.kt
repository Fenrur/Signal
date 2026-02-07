package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that only emits when the selected key changes, with glitch-free semantics.
 *
 * @param T the type of source values
 * @param K the type of the key
 * @param source the source signal
 * @param keySelector function to extract comparison key
 */
class DistinctBySignal<T, K>(
    private val source: Signal<T>,
    private val keySelector: (T) -> K
) : AbstractComputedSignal<T>() {

    override val sources: List<Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)
    private val lastKey: AtomicReference<K>

    override val cachedValue: AtomicReference<T>

    init {
        val initial = source.value
        cachedValue = AtomicReference(initial)
        lastKey = AtomicReference(keySelector(initial))
        lastSourceVersion.set(getVersion(source))
    }

    override fun computeValue(): T {
        val current = source.value
        val currentKey = keySelector(current)
        return if (currentKey != lastKey.get()) {
            lastKey.set(currentKey)
            current
        } else {
            cachedValue.get()
        }
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.get()

    override fun updateSourceVersions() {
        lastSourceVersion.set(getVersion(source))
    }

    override fun toString(): String = "DistinctBySignal(value=$value, isClosed=$isClosed)"
}
