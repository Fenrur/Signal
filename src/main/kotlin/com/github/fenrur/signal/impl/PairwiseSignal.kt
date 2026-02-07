package com.github.fenrur.signal.impl

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
 * Uses lazy subscription to prevent memory leaks.
 *
 * @param T the type of value held by the signal
 * @param source the source signal
 */
class PairwiseSignal<T>(
    private val source: Signal<T>
) : Signal<Pair<T, T>> {

    private val currentPair = AtomicReference(source.value to source.value)
    private val lastSourceValue = AtomicReference(source.value)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<Pair<T, T>>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubscribeSource.set(source.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { newValue ->
                        // Skip if this is the same value we already processed
                        val lastSeen = lastSourceValue.getAndSet(newValue)
                        if (lastSeen != newValue) {
                            val oldPair = currentPair.get()
                            val newPair = oldPair.second to newValue
                            currentPair.set(newPair)
                            notifyAllValue(listeners.toList(), newPair)
                        }
                    },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubscribeSource.getAndSet {}.invoke()
        }
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: Pair<T, T> get() = currentPair.get()

    override fun subscribe(listener: SubscribeListener<Pair<T, T>>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        listener(Result.success(value))
        listeners += listener
        return {
            listeners -= listener
            maybeUnsubscribe()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            if (subscribed.compareAndSet(true, false)) {
                unsubscribeSource.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "PairwiseSignal(value=$value, isClosed=$isClosed)"
}
