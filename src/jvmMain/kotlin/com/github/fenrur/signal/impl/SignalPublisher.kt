package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.UnSubscriber
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import kotlin.concurrent.atomics.*

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
        val unsubscribeRef = AtomicReference<UnSubscriber> {}

        subscriber.onSubscribe(object : Subscription {
            override fun request(n: Long) {
                if (n <= 0) {
                    subscriber.onError(IllegalArgumentException("Request must be positive"))
                    return
                }
                requested.addAndFetch(n)
            }

            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    unsubscribeRef.load().invoke()
                }
            }
        })

        val unsubscribe = signal.subscribe { result ->
            if (cancelled.load()) return@subscribe
            result.fold(
                onSuccess = { value ->
                    if (requested.load() > 0) {
                        requested.decrementAndFetch()
                        subscriber.onNext(value)
                    }
                },
                onFailure = { error ->
                    subscriber.onError(error)
                    cancelled.store(true)
                }
            )
        }
        unsubscribeRef.store(unsubscribe)

        if (signal.isClosed) {
            subscriber.onComplete()
        }
    }
}
