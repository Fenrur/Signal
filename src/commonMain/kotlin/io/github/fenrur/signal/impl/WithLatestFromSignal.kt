package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.Signal] that combines the source with the latest value from another signal,
 * with glitch-free semantics.
 *
 * Emits whenever the source signal changes, combining with the latest
 * value from the other signal. Does NOT emit when only the other signal changes.
 *
 * **Design Note**: This signal intentionally bypasses the MAYBE_DIRTY optimization
 * by always returning `true` from [hasSourcesChanged]. This ensures that when
 * the source emits, we always sample the current value from [other], even if
 * [other] changed independently. Without this, we could miss updates to [other]
 * that occurred between source emissions.
 *
 * @param A the type of source values
 * @param B the type of other signal values
 * @param R the type of combined result
 * @param source the primary source signal (triggers emissions)
 * @param other the signal to sample latest value from
 * @param combiner function to combine values
 */
class WithLatestFromSignal<A, B, R>(
    private val source: io.github.fenrur.signal.Signal<A>,
    private val other: io.github.fenrur.signal.Signal<B>,
    private val combiner: (A, B) -> R
) : io.github.fenrur.signal.impl.AbstractComputedSignal<R>() {

    // Only source triggers notifications, but we sample from other
    override val sources: List<io.github.fenrur.signal.Signal<*>> = listOf(source)

    private val lastSourceVersion = AtomicLong(-1L)

    override val cachedValue: AtomicReference<R> = AtomicReference(combiner(source.value, other.value))

    init {
        lastSourceVersion.store(getVersion(source))
    }

    override fun computeValue(): R = combiner(source.value, other.value)

    /**
     * Always returns true to ensure we sample the latest value from [other].
     *
     * Unlike typical computed signals that can skip recomputation when sources
     * haven't changed, WithLatestFrom must always recompute because:
     * 1. We only track [source] in the dependency graph (not [other])
     * 2. [other] may have changed independently since our last computation
     * 3. We need the freshest value from [other] at the moment [source] emits
     *
     * This is a deliberate trade-off: slightly less efficient but semantically correct.
     */
    override fun hasSourcesChanged(): Boolean = true

    override fun updateSourceVersions() {
        lastSourceVersion.store(getVersion(source))
    }

    override fun toString(): String = "WithLatestFromSignal(value=$value, isClosed=$isClosed)"
}
