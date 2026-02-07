package com.github.fenrur.signal.impl

import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A [MutableSignal] backed by a Kotlin [MutableStateFlow].
 *
 * This signal provides bidirectional synchronization:
 * - Writing to the signal updates the StateFlow and notifies subscribers
 * - External updates to the StateFlow are detected and notify signal subscribers
 *
 * Implements [SourceSignalNode] for glitch-free integration with the dependency graph.
 * Uses lazy subscription - only collects from StateFlow when there are listeners or targets.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param stateFlow the MutableStateFlow to back this signal
 * @param scope the CoroutineScope used to collect from the StateFlow
 */
class MutableStateFlowSignal<T>(
    private val stateFlow: MutableStateFlow<T>,
    private val scope: CoroutineScope
) : MutableSignal<T>, SourceSignalNode {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val lastNotifiedValue = AtomicReference(stateFlow.value)

    // Lazy subscription
    private val subscribed = AtomicBoolean(false)
    private val collectJob = AtomicReference<Job?>(null)

    // Glitch-free infrastructure
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.get()

    // Flag to prevent double notification when we set the value ourselves
    private val selfUpdating = AtomicBoolean(false)

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.set(false)
            if (!closed.get() && listeners.isNotEmpty()) {
                notifyAllValue(listeners.toList(), stateFlow.value)
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            val job = scope.launch {
                stateFlow.collect { newValue ->
                    if (closed.get()) return@collect
                    // Skip if we're the one updating (avoid double notification)
                    if (selfUpdating.get()) return@collect

                    val old = lastNotifiedValue.getAndSet(newValue)
                    if (old != newValue) {
                        // External update detected
                        _version.incrementAndGet()
                        SignalGraph.incrementGlobalVersion()

                        SignalGraph.startBatch()
                        try {
                            for (target in targets) {
                                target.markDirty()
                            }
                            if (listeners.isNotEmpty()) {
                                SignalGraph.scheduleEffect(listenerEffect)
                            }
                        } finally {
                            SignalGraph.endBatch()
                        }
                    }
                }
            }
            collectJob.set(job)
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            collectJob.getAndSet(null)?.cancel()
        }
    }

    override var value: T
        get() = stateFlow.value
        set(newValue) {
            if (closed.get()) return

            // Use CAS loop for thread-safe update
            while (true) {
                val current = stateFlow.value
                if (current == newValue) return

                // Mark that we're updating to prevent collect from double-notifying
                selfUpdating.set(true)
                try {
                    if (stateFlow.compareAndSet(current, newValue)) {
                        lastNotifiedValue.set(newValue)
                        _version.incrementAndGet()
                        SignalGraph.incrementGlobalVersion()

                        SignalGraph.startBatch()
                        try {
                            for (target in targets) {
                                target.markDirty()
                            }
                            if (listeners.isNotEmpty()) {
                                SignalGraph.scheduleEffect(listenerEffect)
                            }
                        } finally {
                            SignalGraph.endBatch()
                        }
                        return
                    }
                } finally {
                    selfUpdating.set(false)
                }
                // CAS failed, retry
                if (closed.get()) return
            }
        }

    override fun update(transform: (T) -> T) {
        if (closed.get()) return
        while (true) {
            val current = stateFlow.value
            val next = transform(current)
            if (current == next) return

            selfUpdating.set(true)
            try {
                if (stateFlow.compareAndSet(current, next)) {
                    lastNotifiedValue.set(next)
                    _version.incrementAndGet()
                    SignalGraph.incrementGlobalVersion()

                    SignalGraph.startBatch()
                    try {
                        for (target in targets) {
                            target.markDirty()
                        }
                        if (listeners.isNotEmpty()) {
                            SignalGraph.scheduleEffect(listenerEffect)
                        }
                    } finally {
                        SignalGraph.endBatch()
                    }
                    return
                }
            } finally {
                selfUpdating.set(false)
            }
            if (closed.get()) return
        }
    }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        listener(Result.success(value))
        listeners += listener
        return {
            listeners -= listener
            maybeUnsubscribe()
        }
    }

    override val isClosed: Boolean get() = closed.get()

    override fun addTarget(target: DirtyMarkable) {
        targets += target
        ensureSubscribed()
    }

    override fun removeTarget(target: DirtyMarkable) {
        targets -= target
        maybeUnsubscribe()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            collectJob.getAndSet(null)?.cancel()
            listeners.clear()
            targets.clear()
            subscribed.set(false)
        }
    }

    override fun toString(): String = "MutableStateFlowSignal(value=$value, version=$version, isClosed=$isClosed)"
}
