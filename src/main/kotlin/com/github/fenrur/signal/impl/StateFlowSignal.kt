package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A read-only [Signal] backed by a Kotlin [StateFlow].
 *
 * This signal observes the StateFlow and notifies subscribers whenever
 * the StateFlow's value changes (from any source).
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param stateFlow the StateFlow to back this signal
 * @param scope the CoroutineScope used to collect from the StateFlow
 */
class StateFlowSignal<T>(
    private val stateFlow: StateFlow<T>,
    scope: CoroutineScope
) : Signal<T> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val lastValue = AtomicReference(stateFlow.value)
    private val collectJob: Job

    init {
        collectJob = scope.launch {
            stateFlow.collect { newValue ->
                if (closed.get()) return@collect
                val old = lastValue.getAndSet(newValue)
                if (old != newValue) {
                    notifyAllValue(listeners.toList(), newValue)
                }
            }
        }
    }

    override val value: T
        get() = stateFlow.value

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (closed.get()) return {}
        listener(Result.success(value))
        listeners += listener
        return { listeners -= listener }
    }

    override val isClosed: Boolean get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            collectJob.cancel()
            listeners.clear()
        }
    }

    override fun toString(): String = "StateFlowSignal(value=$value, isClosed=$isClosed)"
}
