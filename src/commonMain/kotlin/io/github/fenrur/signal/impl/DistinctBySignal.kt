package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.Signal] that only emits when the selected key changes, with glitch-free semantics.
 *
 * @param T the type of source values
 * @param K the type of the key
 * @param source the source signal
 * @param keySelector function to extract comparison key
 */
class DistinctBySignal<T, K>(
    private val source: io.github.fenrur.signal.Signal<T>,
    private val keySelector: (T) -> K
) : io.github.fenrur.signal.impl.AbstractComputedSignal<T>() {

    override val sources: List<io.github.fenrur.signal.Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)
    private val lastKey: AtomicReference<K>

    override val cachedValue: AtomicReference<T>

    init {
        val initial = source.value
        cachedValue = AtomicReference(initial)
        lastKey = AtomicReference(keySelector(initial))
        lastSourceVersion.store(getVersion(source))
    }

    override fun computeValue(): T {
        val current = source.value
        val currentKey = keySelector(current)
        val prevKey = lastKey.exchange(currentKey)
        return if (currentKey != prevKey) {
            current
        } else {
            cachedValue.load()
        }
    }

    override fun hasSourcesChanged(): Boolean = getVersion(source) != lastSourceVersion.load()

    override fun updateSourceVersions() {
        lastSourceVersion.store(getVersion(source))
    }

    override fun toString(): String = "DistinctBySignal(value=$value, isClosed=$isClosed)"
}
