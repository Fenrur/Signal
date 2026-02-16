package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Wrapper to hold an optional value (present or absent).
 */
private sealed class OptionalValue<out T> {
    data class Present<T>(val value: T) : OptionalValue<T>()
    data object Absent : OptionalValue<Nothing>()
}

/**
 * A read-only [Signal] backed by a Java [Flow.Publisher].
 *
 * This signal subscribes to the publisher and updates its value
 * whenever a new item is emitted.
 *
 * Implements [SourceSignalNode] for glitch-free integration with the dependency graph.
 * Uses lazy subscription - only subscribes to publisher when there are listeners or targets.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param publisher the Flow.Publisher to subscribe to
 * @param initial optional initial value before the publisher emits
 * @param request number of items to request from the publisher (default: Long.MAX_VALUE)
 */
class FlowSignal<T>(
    private val publisher: Flow.Publisher<T>,
    initial: T? = null,
    private val hasInitial: Boolean = initial != null,
    private val request: Long = Long.MAX_VALUE
) : Signal<T>, SourceSignalNode {

    private val ref = AtomicReference<OptionalValue<T>>(
        @Suppress("UNCHECKED_CAST")
        if (hasInitial) OptionalValue.Present(initial as T) else OptionalValue.Absent
    )
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)

    // Lazy subscription
    private val subscribed = AtomicBoolean(false)
    private val subscription = AtomicReference<Flow.Subscription?>(null)

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
                when (val v = ref.get()) {
                    is OptionalValue.Present -> notifyAllValue(listeners.toList(), v.value)
                    is OptionalValue.Absent -> { /* No value yet */ }
                }
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            publisher.subscribe(object : Flow.Subscriber<T> {
                override fun onSubscribe(s: Flow.Subscription) {
                    subscription.set(s)
                    if (closed.get()) {
                        subscription.getAndSet(null)?.cancel()
                        return
                    }
                    s.request(request)
                }

                override fun onNext(item: T) {
                    if (closed.get()) return
                    val new = OptionalValue.Present(item)
                    val old = ref.getAndSet(new)
                    if (old != new) {
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

                override fun onError(t: Throwable) {
                    notifyAllError(listeners.toList(), t)
                }

                override fun onComplete() {
                    // no-op
                }
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            subscription.getAndSet(null)?.cancel()

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.get()) {
                ensureSubscribed()
            }
        }
    }

    override val value: T
        get() = when (val v = ref.get()) {
            is OptionalValue.Present -> v.value
            is OptionalValue.Absent -> throw IllegalStateException("Signal has not received any value yet")
        }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        when (val current = ref.get()) {
            is OptionalValue.Present -> listener(Result.success(current.value))
            is OptionalValue.Absent -> { /* No initial emission until a value is received */ }
        }
        listeners += listener
        return {
            listeners -= listener
            maybeUnsubscribe()
        }
    }

    override val isClosed: Boolean
        get() = closed.get()

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
            subscription.getAndSet(null)?.cancel()
            listeners.clear()
            targets.clear()
            subscribed.set(false)
        }
    }

    override fun toString(): String {
        val valueStr = when (val v = ref.get()) {
            is OptionalValue.Present -> v.value.toString()
            is OptionalValue.Absent -> "<no value>"
        }
        return "FlowSignal(value=$valueStr, version=$version, isClosed=$isClosed)"
    }

    companion object {
        /**
         * Creates a FlowSignal with an initial value.
         */
        fun <T> withInitial(publisher: Flow.Publisher<T>, initial: T, request: Long = Long.MAX_VALUE): FlowSignal<T> =
            FlowSignal(publisher, initial, true, request)

        /**
         * Creates a FlowSignal without an initial value.
         */
        fun <T> withoutInitial(publisher: Flow.Publisher<T>, request: Long = Long.MAX_VALUE): FlowSignal<T> =
            FlowSignal(publisher, null, false, request)
    }
}
