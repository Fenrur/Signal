package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A read-only [Signal] backed by a Kotlin [StateFlow].
 *
 * This signal observes the StateFlow and notifies subscribers whenever
 * the StateFlow's value changes (from any source).
 *
 * Implements [SourceSignalNode] for glitch-free integration with the dependency graph.
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
) : Signal<T>, SourceSignalNode {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val lastValue = AtomicReference(stateFlow.value)

    // Lazy subscription
    private val subscribed = AtomicBoolean(false)
    private val collectJob = AtomicReference<Job?>(null)

    // Glitch-free infrastructure
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.get()

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
                    val old = lastValue.getAndSet(newValue)
                    if (old != newValue) {
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

    override val value: T
        get() = stateFlow.value

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

    override fun toString(): String = "StateFlowSignal(value=$value, version=$version, isClosed=$isClosed)"
}
