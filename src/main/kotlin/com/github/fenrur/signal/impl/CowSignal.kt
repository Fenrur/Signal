package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A simple read-only [Signal] implementation with a fixed initial value.
 *
 * This signal holds an immutable value that never changes after creation.
 * Subscribers will receive the initial value immediately upon subscription.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial the value of the signal
 */
class CowSignal<T>(initial: T) : Signal<T> {

    private val ref = AtomicReference(initial)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)

    override val value: T
        get() = ref.get()

    override val isClosed: Boolean
        get() = closed.get()

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
        }
    }

    override fun toString(): String = "CowSignal(value=$value, isClosed=$isClosed)"
}
