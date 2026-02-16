package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.atomics.*

/**
 * A read-only [io.github.fenrur.signal.Signal] backed by a Kotlin [StateFlow].
 *
 * This signal observes the StateFlow and notifies subscribers whenever
 * the StateFlow's value changes (from any source).
 *
 * Implements [io.github.fenrur.signal.impl.SourceSignalNode] for glitch-free integration with the dependency graph.
 * Uses lazy subscription - only collects from StateFlow when there are listeners or targets.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param stateFlow the StateFlow to back this signal
 * @param scope the CoroutineScope used to collect from the StateFlow
 */
class StateFlowSignal<T>(
    private val stateFlow: StateFlow<T>,
    private val scope: CoroutineScope
) : io.github.fenrur.signal.Signal<T>, io.github.fenrur.signal.impl.SourceSignalNode {

    private val listeners = CopyOnWriteArrayList<io.github.fenrur.signal.SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val lastValue = AtomicReference(stateFlow.value)

    // Lazy subscription
    private val subscribed = AtomicBoolean(false)
    private val collectJob = AtomicReference<Job?>(null)

    // Glitch-free infrastructure
    private val targets = CopyOnWriteArrayList<io.github.fenrur.signal.impl.DirtyMarkable>()
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.load()

    private val listenerEffect = object : io.github.fenrur.signal.impl.EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                _root_ide_package_.io.github.fenrur.signal.impl.notifyAllValue(listeners, stateFlow.value)
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            val job = scope.launch {
                stateFlow.collect { newValue ->
                    if (closed.load()) return@collect
                    val old = lastValue.exchange(newValue)
                    if (old != newValue) {
                        _version.incrementAndFetch()
                        _root_ide_package_.io.github.fenrur.signal.impl.SignalGraph.incrementGlobalVersion()

                        _root_ide_package_.io.github.fenrur.signal.impl.SignalGraph.startBatch()
                        try {
                            for (target in targets) {
                                target.markDirty()
                            }
                            if (listeners.isNotEmpty()) {
                                _root_ide_package_.io.github.fenrur.signal.impl.SignalGraph.scheduleEffect(listenerEffect)
                            }
                        } finally {
                            _root_ide_package_.io.github.fenrur.signal.impl.SignalGraph.endBatch()
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

    override val value: T
        get() = stateFlow.value

    override fun subscribe(listener: io.github.fenrur.signal.SubscribeListener<T>): io.github.fenrur.signal.UnSubscriber {
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

    override fun addTarget(target: io.github.fenrur.signal.impl.DirtyMarkable) {
        targets += target
        ensureSubscribed()
    }

    override fun removeTarget(target: io.github.fenrur.signal.impl.DirtyMarkable) {
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

    override fun toString(): String = "StateFlowSignal(value=$value, version=$version, isClosed=$isClosed)"
}
