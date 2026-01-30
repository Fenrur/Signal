package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that emits pairs of consecutive values.
 *
 * Each emission contains the previous value and the current value.
 * The first emission happens after the source has emitted at least two distinct values.
 *
 * @param T the type of value held by the signal
 * @param source the source signal
 */
class PairwiseSignal<T>(
    source: Signal<T>
) : Signal<Pair<T, T>> {

    private val previous = AtomicReference(source.value)
    private val ref = AtomicReference(source.value to source.value)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<Pair<T, T>>>()
    private val closed = AtomicBoolean(false)
    private val unsubscribeSource: UnSubscriber

    init {
        unsubscribeSource = source.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { newValue ->
                    val prev = previous.getAndSet(newValue)
                    val newPair = prev to newValue
                    val oldPair = ref.getAndSet(newPair)
                    if (oldPair != newPair) {
                        notifyAllValue(listeners.toList(), newPair)
                    }
                }
            )
        }
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: Pair<T, T> get() = ref.get()

    override fun subscribe(listener: SubscribeListener<Pair<T, T>>): UnSubscriber {
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

    override fun toString(): String = "PairwiseSignal(value=$value, isClosed=$isClosed)"
}
