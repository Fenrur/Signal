package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Lock-based implementation of [MutableSignal].
 *
 * This implementation uses explicit [ReentrantLock] for synchronization,
 * useful when you need broader critical sections or fair locking.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial the initial value of the signal
 */
class LockedSignal<T>(initial: T) : MutableSignal<T> {

    private val ref = AtomicReference(initial)
    private val listeners = mutableListOf<SubscribeListener<T>>()
    private val lock = ReentrantLock()
    private val closed = AtomicBoolean(false)

    override val isClosed: Boolean
        get() = closed.get()

    override var value: T
        get() = ref.get()
        set(new) {
            if (isClosed) return
            val snapshot: List<SubscribeListener<T>>
            val old: T
            lock.lock()
            try {
                old = ref.getAndSet(new)
                snapshot = if (old != new) listeners.toList() else emptyList()
            } finally {
                lock.unlock()
            }
            if (old != new && !isClosed) notifyAllValue(snapshot, new)
        }

    override fun update(transform: (T) -> T) {
        if (isClosed) return
        val snapshot: List<SubscribeListener<T>>
        val next: T
        var changed = false
        lock.lock()
        try {
            val cur = ref.get()
            val computed = transform(cur)
            if (cur != computed) {
                ref.set(computed)
                next = computed
                snapshot = listeners.toList()
                changed = true
            } else {
                return
            }
        } finally {
            lock.unlock()
        }
        if (changed && !isClosed) notifyAllValue(snapshot, next)
    }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        val initial = value
        lock.lock()
        try {
            listener(Either.Right(initial))
            listeners += listener
        } finally {
            lock.unlock()
        }
        return {
            lock.lock()
            try {
                listeners -= listener
            } finally {
                lock.unlock()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            lock.lock()
            try {
                listeners.clear()
            } finally {
                lock.unlock()
            }
        }
    }

    override fun toString(): String = "LockedSignal(value=$value, isClosed=$isClosed)"
}
