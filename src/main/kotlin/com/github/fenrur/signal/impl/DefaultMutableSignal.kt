package com.github.fenrur.signal.impl

import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Copy-On-Write implementation of [MutableSignal] with glitch-free semantics.
 *
 * This implementation uses:
 * - [CopyOnWriteArrayList] for thread-safe listener management
 * - Version counting for efficient staleness detection
 * - Push-based dirty marking for the dependency graph
 * - Batching support for atomic multi-signal updates
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial the initial value of the signal
 */
class DefaultMutableSignal<T>(initial: T) : MutableSignal<T>, SourceSignalNode {

    private val ref = AtomicReference(initial)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)

    /**
     * Targets in the dependency graph (computed signals that depend on this).
     */
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()

    /**
     * Local version counter. Incremented when value changes.
     */
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.get()

    override val isClosed: Boolean
        get() = closed.get()

    override var value: T
        get() = ref.get()
        set(new) {
            if (isClosed) return
            val old = ref.getAndSet(new)
            if (old != new) {
                // Increment versions
                _version.incrementAndGet()
                SignalGraph.incrementGlobalVersion()

                // Start batch to collect all effects
                SignalGraph.startBatch()
                try {
                    // PUSH phase: mark all targets as dirty
                    for (target in targets) {
                        target.markDirty()
                    }

                    // Schedule effect execution for listeners
                    if (listeners.isNotEmpty()) {
                        scheduleListenerNotification(new)
                    }
                } finally {
                    SignalGraph.endBatch()
                }
            }
        }

    override fun update(transform: (T) -> T) {
        if (isClosed) return
        while (true) {
            val cur = ref.get()
            val next = transform(cur)
            if (cur == next) return
            if (ref.compareAndSet(cur, next)) {
                if (!isClosed) {
                    // Increment versions
                    _version.incrementAndGet()
                    SignalGraph.incrementGlobalVersion()

                    // Start batch to collect all effects
                    SignalGraph.startBatch()
                    try {
                        // PUSH phase: mark all targets as dirty
                        for (target in targets) {
                            target.markDirty()
                        }

                        // Schedule effect execution for listeners
                        if (listeners.isNotEmpty()) {
                            scheduleListenerNotification(next)
                        }
                    } finally {
                        SignalGraph.endBatch()
                    }
                }
                return
            }
            if (isClosed) return
        }
    }

    private fun scheduleListenerNotification(newValue: T) {
        val effect = object : EffectNode {
            private val pending = AtomicBoolean(false)

            override fun markPending(): Boolean = pending.compareAndSet(false, true)

            override fun execute() {
                pending.set(false)
                if (!isClosed) {
                    // Read current value at execution time for consistency
                    val currentValue = ref.get()
                    notifyAllValue(listeners.toList(), currentValue)
                }
            }
        }
        SignalGraph.scheduleEffect(effect)
    }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        listener(Result.success(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun addTarget(target: DirtyMarkable) {
        targets += target
    }

    override fun removeTarget(target: DirtyMarkable) {
        targets -= target
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            targets.clear()
        }
    }

    override fun toString(): String = "DefaultMutableSignal(value=$value, version=$version, isClosed=$isClosed)"
}
