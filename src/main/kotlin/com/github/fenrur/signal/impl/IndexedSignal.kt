package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Set-based implementation of [MutableSignal].
 *
 * This implementation uses a [ConcurrentHashMap] key set for listeners,
 * providing O(1) lookup for subscribe/unsubscribe operations.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial the initial value of the signal
 */
class IndexedSignal<T>(initial: T) : MutableSignal<T> {

    private val ref = AtomicReference(initial)
    private val listeners = ConcurrentHashMap.newKeySet<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)

    override val isClosed: Boolean
        get() = closed.get()

    override var value: T
        get() = ref.get()
        set(new) {
            if (isClosed) return
            val old = ref.getAndSet(new)
            if (old != new) notifyAllValue(listeners.toList(), new)
        }

    override fun update(transform: (T) -> T) {
        if (isClosed) return
        while (true) {
            val cur = ref.get()
            val next = transform(cur)
            if (cur == next) return
            if (ref.compareAndSet(cur, next)) {
                if (!isClosed) notifyAllValue(listeners.toList(), next)
                return
            }
            if (isClosed) return
        }
    }

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

    override fun toString(): String = "IndexedSignal(value=$value, isClosed=$isClosed)"
}
