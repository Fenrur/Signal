package io.github.fenrur.signal.impl

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

/**
 * Copy-On-Write implementation of [io.github.fenrur.signal.MutableSignal] with glitch-free semantics.
 *
 * This implementation uses:
 * - [io.github.fenrur.signal.impl.CopyOnWriteArrayList] for thread-safe listener management
 * - Version counting for efficient staleness detection
 * - Push-based dirty marking for the dependency graph
 * - Batching support for atomic multi-signal updates
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial the initial value of the signal
 */
class DefaultMutableSignal<T>(initial: T) : MutableSignal<T>,
    SourceSignalNode {

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
    override val version: Long get() = _version.load()

    override val isClosed: Boolean
        get() = closed.load()

    override var value: T
        get() = ref.load()
        set(new) {
            if (isClosed) return
            val old = ref.exchange(new)
            if (old != new) {
                // Increment versions
                _version.incrementAndFetch()
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
            val cur = ref.load()
            val next = transform(cur)
            if (cur == next) return
            if (ref.compareAndSet(cur, next)) {
                if (!isClosed) {
                    // Increment versions
                    _version.incrementAndFetch()
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
                pending.store(false)
                if (!isClosed) {
                    // Read current value at execution time for consistency
                    val currentValue = ref.load()
                    notifyAllValue(listeners, currentValue)
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
