package io.github.fenrur.signal.impl

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.MutableSignal] backed by a Kotlin [MutableStateFlow].
 *
 * This signal provides bidirectional synchronization:
 * - Writing to the signal updates the StateFlow and notifies subscribers
 * - External updates to the StateFlow are detected and notify signal subscribers
 *
 * Implements [io.github.fenrur.signal.impl.SourceSignalNode] for glitch-free integration with the dependency graph.
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
    override val version: Long get() = _version.load()

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
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                notifyAllValue(listeners, stateFlow.value)
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            val job = scope.launch {
                stateFlow.collect { newValue ->
                    if (closed.load()) return@collect

                    // Skip if we're the one updating (avoid double notification).
                    // Check if the current version matches our self-update version.
                    val currentVersion = _version.load()
                    if (selfUpdateVersion.load() == currentVersion) return@collect

                    // Defense-in-depth: also check if value actually changed
                    val old = lastNotifiedValue.exchange(newValue)
                    if (old != newValue) {
                        // External update detected
                        _version.incrementAndFetch()
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
            collectJob.store(job)

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.load()) {
                collectJob.exchange(null)?.cancel()
            }
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            collectJob.exchange(null)?.cancel()

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.load()) {
                ensureSubscribed()
            }
        }
    }

    override var value: T
        get() = stateFlow.value
        set(newValue) {
            if (closed.load()) return

            // Use CAS loop for thread-safe update
            while (true) {
                val current = stateFlow.value
                if (current == newValue) return

                if (stateFlow.compareAndSet(current, newValue)) {
                    lastNotifiedValue.store(newValue)
                    val newVersion = _version.incrementAndFetch()
                    SignalGraph.incrementGlobalVersion()

                    // Mark the version we're updating to prevent collector from double-notifying.
                    // This is set AFTER incrementing version so the collector can check it.
                    selfUpdateVersion.store(newVersion)
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
                        selfUpdateVersion.store(-1L)
                    }
                    return
                }
                // CAS failed, retry
                if (closed.load()) return
            }
        }

    override fun update(transform: (T) -> T) {
        if (closed.load()) return
        while (true) {
            val current = stateFlow.value
            val next = transform(current)
            if (current == next) return

            if (stateFlow.compareAndSet(current, next)) {
                lastNotifiedValue.store(next)
                val newVersion = _version.incrementAndFetch()
                SignalGraph.incrementGlobalVersion()

                // Mark the version we're updating to prevent collector from double-notifying
                selfUpdateVersion.store(newVersion)
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
                    selfUpdateVersion.store(-1L)
                }
                return
            }
            if (closed.load()) return
        }
    }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (closed.load()) return {}
        ensureSubscribed()
        listener(Result.success(value))
        listeners += listener
        return {
            listeners -= listener
            maybeUnsubscribe()
        }
    }

    override val isClosed: Boolean get() = closed.load()

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
            collectJob.exchange(null)?.cancel()
            listeners.clear()
            targets.clear()
            subscribed.store(false)
        }
    }

    override fun toString(): String = "MutableStateFlowSignal(value=$value, version=$version, isClosed=$isClosed)"
}
