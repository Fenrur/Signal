package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
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
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param publisher the Flow.Publisher to subscribe to
 * @param initial optional initial value before the publisher emits
 * @param request number of items to request from the publisher (default: Long.MAX_VALUE)
 */
class FlowSignal<T>(
    publisher: Flow.Publisher<T>,
    initial: T? = null,
    private val hasInitial: Boolean = initial != null,
    request: Long = Long.MAX_VALUE
) : Signal<T> {

    private val ref = AtomicReference<OptionalValue<T>>(
        @Suppress("UNCHECKED_CAST")
        if (hasInitial) OptionalValue.Present(initial as T) else OptionalValue.Absent
    )
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)

    @Volatile
    private var subscription: Flow.Subscription? = null

    init {
        publisher.subscribe(object : Flow.Subscriber<T> {
            override fun onSubscribe(s: Flow.Subscription) {
                subscription = s
                s.request(request)
            }

            override fun onNext(item: T) {
                if (closed.get()) return
                val new = OptionalValue.Present(item)
                val old = ref.getAndSet(new)
                if (old != new) notifyAllValue(listeners.toList(), item)
            }

            override fun onError(t: Throwable) {
                notifyAllError(listeners.toList(), t)
            }

            override fun onComplete() {
                // no-op
            }
        })
    }

    override val value: T
        get() = when (val v = ref.get()) {
            is OptionalValue.Present -> v.value
            is OptionalValue.Absent -> throw IllegalStateException("Signal has not received any value yet")
        }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (closed.get()) return {}
        when (val current = ref.get()) {
            is OptionalValue.Present -> listener(Either.Right(current.value))
            is OptionalValue.Absent -> { /* No initial emission until a value is received */ }
        }
        listeners += listener
        return { listeners -= listener }
    }

    override val isClosed: Boolean
        get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            subscription?.cancel()
            listeners.clear()
        }
    }

    override fun toString(): String {
        val valueStr = when (val v = ref.get()) {
            is OptionalValue.Present -> v.value.toString()
            is OptionalValue.Absent -> "<no value>"
        }
        return "FlowSignal(value=$valueStr, isClosed=$isClosed)"
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
