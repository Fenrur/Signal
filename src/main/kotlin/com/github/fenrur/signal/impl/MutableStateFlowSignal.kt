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
 * ## Thread-Safety
 *
 * All operations are thread-safe. The implementation uses a dual-guard pattern to prevent
 * double notifications when updating via signal vs external StateFlow updates:
 *
 * 1. **selfUpdateVersion**: Tracks which version update is currently in progress from the signal side.
 *    The collector checks this and skips if the current version matches (we're updating ourselves).
 *
 * 2. **lastNotifiedValue**: Provides defense-in-depth by tracking the last value notified to subscribers.
 *    Even if the version check fails due to timing, duplicate values are filtered out.
 *
 * This two-level protection ensures no duplicate notifications under any interleaving of:
 * - Concurrent signal updates from multiple threads
 * - Concurrent external StateFlow updates
 * - Coroutine collector executing at any point
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

    /**
     * Tracks the version we're currently updating to from the signal side.
     * -1 means no self-update in progress.
     * The collector skips notifications when this matches the current version being set.
     */
    private val selfUpdateVersion = AtomicLong(-1L)

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

                    // Skip if we're the one updating (avoid double notification).
                    // Check if the current version matches our self-update version.
                    val currentVersion = _version.get()
                    if (selfUpdateVersion.get() == currentVersion) return@collect

                    // Defense-in-depth: also check if value actually changed
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

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.get()) {
                collectJob.getAndSet(null)?.cancel()
            }
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            collectJob.getAndSet(null)?.cancel()

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.get()) {
                ensureSubscribed()
            }
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

                if (stateFlow.compareAndSet(current, newValue)) {
                    lastNotifiedValue.set(newValue)
                    val newVersion = _version.incrementAndGet()
                    SignalGraph.incrementGlobalVersion()

                    // Mark the version we're updating to prevent collector from double-notifying.
                    // This is set AFTER incrementing version so the collector can check it.
                    selfUpdateVersion.set(newVersion)
                    try {
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
                    } finally {
                        selfUpdateVersion.set(-1L)
                    }
                    return
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

            if (stateFlow.compareAndSet(current, next)) {
                lastNotifiedValue.set(next)
                val newVersion = _version.incrementAndGet()
                SignalGraph.incrementGlobalVersion()

                // Mark the version we're updating to prevent collector from double-notifying
                selfUpdateVersion.set(newVersion)
                try {
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
                } finally {
                    selfUpdateVersion.set(-1L)
                }
                return
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
