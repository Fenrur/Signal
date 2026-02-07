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
import java.util.concurrent.atomic.AtomicReference

/**
 * A [MutableSignal] backed by a Kotlin [MutableStateFlow].
 *
 * This signal provides bidirectional synchronization:
 * - Writing to the signal updates the StateFlow and notifies subscribers
 * - External updates to the StateFlow are detected and notify signal subscribers
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param stateFlow the MutableStateFlow to back this signal
 * @param scope the CoroutineScope used to collect from the StateFlow
 */
class MutableStateFlowSignal<T>(
    private val stateFlow: MutableStateFlow<T>,
    scope: CoroutineScope
) : MutableSignal<T> {

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

    override var value: T
        get() = stateFlow.value
        set(newValue) {
            if (closed.get()) return
            val old = stateFlow.value
            if (old == newValue) return
            // Update lastValue first to prevent double notification from collect
            lastValue.set(newValue)
            stateFlow.value = newValue
            // Notify listeners directly (collect won't notify since lastValue already updated)
            notifyAllValue(listeners.toList(), newValue)
        }

    override fun update(transform: (T) -> T) {
        if (closed.get()) return
        while (true) {
            val current = stateFlow.value
            val new = transform(current)
            if (current == new) return
            // Try to update atomically
            lastValue.set(new)
            if (stateFlow.compareAndSet(current, new)) {
                // Notify listeners directly
                notifyAllValue(listeners.toList(), new)
                return
            }
        }
    }

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

    override fun toString(): String = "MutableStateFlowSignal(value=$value, isClosed=$isClosed)"
}
