package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that flattens a nested Signal<Signal<T>> to Signal<T>.
 *
 * When the outer signal changes, this signal switches to the new inner signal.
 * Similar to RxJS switchMap or Kotlin's flatMapLatest.
 * Uses lazy subscription to prevent memory leaks.
 *
 * @param T the type of the inner signal value
 * @param source the outer signal containing inner signals
 */
class FlattenSignal<T>(
    private val source: Signal<Signal<T>>
) : Signal<T> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val innerUnsubscribe = AtomicReference<UnSubscriber> {}
    private val unsubscribeOuter = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            // Subscribe to inner signal initially
            subscribeToInner(source.value)

            // Subscribe to outer signal to switch inner subscriptions
            unsubscribeOuter.set(source.subscribe { either ->
                if (closed.get()) return@subscribe
                either.fold(
                    { ex -> notifyAllError(listeners.toList(), ex) },
                    { innerSignal ->
                        // Unsubscribe from previous inner
                        innerUnsubscribe.getAndSet {}.invoke()
                        // Subscribe to new inner
                        subscribeToInner(innerSignal)
                    }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            innerUnsubscribe.getAndSet {}.invoke()
            unsubscribeOuter.getAndSet {}.invoke()
        }
    }

    private fun subscribeToInner(inner: Signal<T>) {
        val unsub = inner.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { value -> notifyAllValue(listeners.toList(), value) }
            )
        }
        innerUnsubscribe.set(unsub)
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: T get() = source.value.value

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
                innerUnsubscribe.getAndSet {}.invoke()
                unsubscribeOuter.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "FlattenSignal(value=$value, isClosed=$isClosed)"
}
