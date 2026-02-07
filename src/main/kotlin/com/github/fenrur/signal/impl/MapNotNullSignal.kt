package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that transforms values and filters out nulls.
 *
 * When the transform returns null, the signal retains its previous non-null value.
 * Uses lazy subscription to prevent memory leaks.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param S the type of source values
 * @param R the type of transformed non-null values
 * @param source the source signal
 * @param transform transformation that may return null
 * @throws IllegalStateException if initial value transforms to null
 */
class MapNotNullSignal<S, R : Any>(
    private val source: Signal<S>,
    private val transform: (S) -> R?
) : Signal<R> {

    private val lastNonNullValue: AtomicReference<R>
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    init {
        val initialTransformed = transform(source.value)
            ?: throw IllegalStateException("mapNotNull requires initial value to transform to non-null")
        lastNonNullValue = AtomicReference(initialTransformed)
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubscribeSource.set(source.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { sv ->
                        val transformed = transform(sv)
                        if (transformed != null) {
                            lastNonNullValue.set(transformed)
                            notifyAllValue(listeners.toList(), transformed)
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

    override val value: R
        get() {
            val current = transform(source.value)
            return current ?: lastNonNullValue.get()
        }

    override val isClosed: Boolean get() = closed.get()

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

    override fun toString(): String = "MapNotNullSignal(value=$value, isClosed=$isClosed)"
}
