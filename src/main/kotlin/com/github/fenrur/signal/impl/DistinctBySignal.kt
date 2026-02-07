package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that only emits when the selected key changes.
 *
 * Unlike the base signal's equality check, this allows custom key extraction
 * for determining distinctness.
 * Uses lazy subscription to prevent memory leaks.
 *
 * @param T the type of value held by the signal
 * @param K the type of the key used for comparison
 * @param source the source signal
 * @param keySelector function to extract the comparison key
 */
class DistinctBySignal<T, K>(
    private val source: Signal<T>,
    private val keySelector: (T) -> K
) : Signal<T> {

    private val ref = AtomicReference(source.value)
    private val lastKey = AtomicReference(keySelector(source.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubscribeSource.set(source.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { newValue ->
                        val newKey = keySelector(newValue)
                        val oldKey = lastKey.get()
                        if (oldKey != newKey) {
                            lastKey.set(newKey)
                            ref.set(newValue)
                            notifyAllValue(listeners.toList(), newValue)
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
    override val value: T get() = ref.get()

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
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

    override fun toString(): String = "DistinctBySignal(value=$value, isClosed=$isClosed)"
}
