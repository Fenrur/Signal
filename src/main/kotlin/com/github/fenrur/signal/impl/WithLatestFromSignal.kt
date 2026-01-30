package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that combines the source with the latest value from another signal.
 *
 * Emits whenever the source signal changes, combining with the latest
 * value from the other signal. Does NOT emit when only the other signal changes.
 *
 * @param A the type of source values
 * @param B the type of other signal values
 * @param R the type of combined result
 * @param source the primary source signal (triggers emissions)
 * @param other the signal to sample latest value from
 * @param combiner function to combine values
 */
class WithLatestFromSignal<A, B, R>(
    private val source: Signal<A>,
    private val other: Signal<B>,
    private val combiner: (A, B) -> R
) : Signal<R> {

    private val ref = AtomicReference(combiner(source.value, other.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubscribeSource: UnSubscriber

    init {
        // Only subscribe to source - we sample other on each source emission
        unsubscribeSource = source.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { sourceValue ->
                    val combined = combiner(sourceValue, other.value)
                    val old = ref.getAndSet(combined)
                    if (old != combined) {
                        notifyAllValue(listeners.toList(), combined)
                    }
                }
            )
        }
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: R get() = ref.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (isClosed) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            unsubscribeSource()
        }
    }

    override fun toString(): String = "WithLatestFromSignal(value=$value, isClosed=$isClosed)"
}
