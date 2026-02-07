package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A derived read-only [Signal] that transforms values from a source signal.
 *
 * This signal applies a transformation function to each value emitted by the source signal.
 * It uses lazy subscription: only subscribes to the source when it has subscribers,
 * and unsubscribes when all subscribers are removed. This prevents memory leaks.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param S the type of the source signal value
 * @param R the type of the transformed value
 * @param source the source signal to transform
 * @param transform the transformation function
 */
class MappedSignal<S, R>(
    private val source: Signal<S>,
    private val transform: (S) -> R
) : Signal<R> {

    private val lastNotified = AtomicReference(transform(source.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubscribeSource.set(source.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { sv ->
                        val new = transform(sv)
                        val old = lastNotified.getAndSet(new)
                        if (old != new) {
                            notifyAllValue(listeners.toList(), new)
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

    override val isClosed: Boolean
        get() = closed.get()

    override val value: R
        get() = transform(source.value)

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
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

    override fun toString(): String = "MappedSignal(value=$value, isClosed=$isClosed)"
}
