package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Wrapper to hold an optional value (present or absent).
 */
private sealed class ReactiveOptionalValue<out T> {
    data class Present<T>(val value: T) : ReactiveOptionalValue<T>()
    data object Absent : ReactiveOptionalValue<Nothing>()
}

/**
 * A [Signal] backed by a Reactive Streams [Publisher].
 *
 * This signal subscribes to the publisher and updates its value
 * whenever a new item is emitted.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param publisher the Reactive Streams Publisher to subscribe to
 * @param initial optional initial value before the publisher emits
 * @param hasInitial whether an initial value was provided
 * @param request number of items to request from the publisher (default: Long.MAX_VALUE)
 */
class ReactiveStreamsSignal<T>(
    publisher: Publisher<T>,
    initial: T? = null,
    private val hasInitial: Boolean = initial != null,
    request: Long = Long.MAX_VALUE
) : Signal<T> {

    private val ref = AtomicReference<ReactiveOptionalValue<T>>(
        @Suppress("UNCHECKED_CAST")
        if (hasInitial) ReactiveOptionalValue.Present(initial as T) else ReactiveOptionalValue.Absent
    )
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)

    @Volatile
    private var subscription: Subscription? = null

    init {
        publisher.subscribe(object : Subscriber<T> {
            override fun onSubscribe(s: Subscription) {
                subscription = s
                s.request(request)
            }

            override fun onNext(item: T) {
                if (closed.get()) return
                val new = ReactiveOptionalValue.Present(item)
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
            is ReactiveOptionalValue.Present -> v.value
            is ReactiveOptionalValue.Absent -> throw IllegalStateException("Signal has not received any value yet")
        }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (closed.get()) return {}
        when (val current = ref.get()) {
            is ReactiveOptionalValue.Present -> listener(Result.success(current.value))
            is ReactiveOptionalValue.Absent -> { /* No initial emission until a value is received */ }
        }
        listeners += listener
        return { listeners -= listener }
    }

    override val isClosed: Boolean get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            subscription?.cancel()
            listeners.clear()
        }
    }

    override fun toString(): String {
        val valueStr = when (val v = ref.get()) {
            is ReactiveOptionalValue.Present -> v.value.toString()
            is ReactiveOptionalValue.Absent -> "<no value>"
        }
        return "ReactiveStreamsSignal(value=$valueStr, isClosed=$isClosed)"
    }

    companion object {
        /**
         * Creates a ReactiveStreamsSignal with an initial value.
         */
        fun <T> withInitial(publisher: Publisher<T>, initial: T, request: Long = Long.MAX_VALUE): ReactiveStreamsSignal<T> =
            ReactiveStreamsSignal(publisher, initial, true, request)

        /**
         * Creates a ReactiveStreamsSignal without an initial value.
         */
        fun <T> withoutInitial(publisher: Publisher<T>, request: Long = Long.MAX_VALUE): ReactiveStreamsSignal<T> =
            ReactiveStreamsSignal(publisher, null, false, request)
    }
}
