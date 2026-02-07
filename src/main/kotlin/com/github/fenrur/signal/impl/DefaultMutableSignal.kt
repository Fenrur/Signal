package com.github.fenrur.signal.impl

import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Copy-On-Write implementation of [MutableSignal].
 *
 * This implementation uses a [CopyOnWriteArrayList] for listeners,
 * making it very safe for read-intensive scenarios with occasional writes.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial the initial value of the signal
 */
class DefaultMutableSignal<T>(initial: T) : MutableSignal<T> {

    private val ref = AtomicReference(initial)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
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
        listener(Result.success(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
        }
    }

    override fun toString(): String = "DefaultMutableSignal(value=$value, isClosed=$isClosed)"
}
