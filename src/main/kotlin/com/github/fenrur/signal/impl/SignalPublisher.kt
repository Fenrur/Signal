package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.UnSubscriber
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A Reactive Streams [Publisher] that wraps a [Signal].
 *
 * The publisher will emit the current value immediately upon subscription,
 * and then emit new values whenever the signal changes.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value emitted by the publisher
 * @param signal the signal to wrap
 */
class SignalPublisher<T>(private val signal: Signal<T>) : Publisher<T> {

    override fun subscribe(subscriber: Subscriber<in T>) {
        val requested = AtomicLong(0)
        val cancelled = AtomicBoolean(false)
        var unsubscribe: UnSubscriber? = null

        subscriber.onSubscribe(object : Subscription {
            override fun request(n: Long) {
                if (n <= 0) {
                    subscriber.onError(IllegalArgumentException("Request must be positive"))
                    return
                }
                requested.addAndGet(n)
            }

            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    unsubscribe?.invoke()
                }
            }
        })

        unsubscribe = signal.subscribe { result ->
            if (cancelled.get()) return@subscribe
            result.fold(
                onSuccess = { value ->
                    if (requested.get() > 0) {
                        requested.decrementAndGet()
                        subscriber.onNext(value)
                    }
                },
                onFailure = { error ->
                    subscriber.onError(error)
                    cancelled.set(true)
                }
            )
        }

        if (signal.isClosed) {
            subscriber.onComplete()
        }
    }
}
