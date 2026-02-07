package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A bidirectionally-mapped [MutableSignal] that transforms values in both directions.
 *
 * Reading applies [forward] to the source value, writing applies [reverse] before
 * setting the source. Subscribers receive forward-transformed values.
 * Uses lazy subscription to prevent memory leaks.
 *
 * Thread-safety: All operations are thread-safe, delegating to the source signal's
 * thread-safety guarantees.
 *
 * @param S the type of the source signal value
 * @param R the type of the mapped signal value
 * @param source the underlying mutable signal
 * @param forward transforms source values to mapped values (read direction)
 * @param reverse transforms mapped values back to source values (write direction)
 */
class BimappedSignal<S, R>(
    private val source: MutableSignal<S>,
    private val forward: (S) -> R,
    private val reverse: (R) -> S
) : MutableSignal<R> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
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
                        val mapped = forward(sv)
                        notifyAllValue(listeners.toList(), mapped)
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

    override val isClosed: Boolean
        get() = closed.get()

    override var value: R
        get() = forward(source.value)
        set(new) {
            if (isClosed) return
            source.value = reverse(new)
        }

    override fun update(transform: (R) -> R) {
        if (isClosed) return
        source.update { s ->
            val mapped = forward(s)
            val transformed = transform(mapped)
            reverse(transformed)
        }
    }

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
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

    override fun toString(): String = "BimappedSignal(value=$value, isClosed=$isClosed)"
}
