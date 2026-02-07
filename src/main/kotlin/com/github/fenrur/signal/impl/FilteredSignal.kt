package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that filters values based on a predicate.
 *
 * When the predicate returns false, the signal retains its previous matching value.
 * Uses lazy subscription to prevent memory leaks.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param source the source signal
 * @param predicate filter condition
 */
class FilteredSignal<T>(
    private val source: Signal<T>,
    private val predicate: (T) -> Boolean
) : Signal<T> {

    private val lastMatchingValue = AtomicReference(source.value)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubscribeSource.set(source.subscribe { either ->
                if (closed.get()) return@subscribe
                either.fold(
                    { ex -> notifyAllError(listeners.toList(), ex) },
                    { sv ->
                        if (predicate(sv)) {
                            lastMatchingValue.set(sv)
                            notifyAllValue(listeners.toList(), sv)
                        }
                    }
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

    override val value: T
        get() = if (predicate(source.value)) source.value else lastMatchingValue.get()

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        listener(Either.Right(value))
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

    override fun toString(): String = "FilteredSignal(value=$value, isClosed=$isClosed)"
}
