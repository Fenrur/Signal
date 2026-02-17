package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

/**
 * A simple read-only [io.github.fenrur.signal.Signal] implementation with a fixed initial value.
 *
 * This signal holds an immutable value that never changes after creation.
 * Subscribers will receive the initial value immediately upon subscription.
 *
 * ## Thread-Safety
 *
 * All operations are thread-safe using atomic operations.
 *
 * ## SourceSignalNode Implementation
 *
 * Implements [io.github.fenrur.signal.impl.SourceSignalNode] for consistency with other source signals,
 * even though the value never changes. The version is always 1 and targets
 * are tracked but never notified.
 *
 * @param T the type of value held by the signal
 * @param initial the value of the signal
 */
class DefaultSignal<T>(initial: T) : Signal<T>, SourceSignalNode {

    private val ref = AtomicReference(initial)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val _version = AtomicLong(1L)

    override val version: Long get() = _version.load()

    override val value: T
        get() = ref.load()

    override val isClosed: Boolean
        get() = closed.load()

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        listener(Result.success(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            targets.clear()
        }
    }

    override fun addTarget(target: DirtyMarkable) {
        targets += target
    }

    override fun removeTarget(target: DirtyMarkable) {
        targets -= target
    }

    override fun toString(): String = "DefaultSignal(value=$value, isClosed=$isClosed)"
}
