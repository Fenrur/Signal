package com.github.fenrur.signal.reactive

import com.github.fenrur.signal.asReactiveStreamsPublisher
import com.github.fenrur.signal.asSignal
import com.github.fenrur.signal.mutableSignalOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SignalReactiveStreamsTest {

    @Test
    fun `asPublisher emits current value on subscribe`() {
        val signal = mutableSignalOf(42)
        val publisher = signal.asReactiveStreamsPublisher()

        val receivedValue = AtomicReference<Int>()
        val latch = CountDownLatch(1)

        publisher.subscribe(object : Subscriber<Int> {
            override fun onSubscribe(s: Subscription) {
                s.request(1)
            }

            override fun onNext(t: Int) {
                receivedValue.set(t)
                latch.countDown()
            }

            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(receivedValue.get()).isEqualTo(42)
    }

    @Test
    fun `asPublisher emits new values when signal changes`() {
        val signal = mutableSignalOf(1)
        val publisher = signal.asReactiveStreamsPublisher()

        val receivedValues = mutableListOf<Int>()
        val latch = CountDownLatch(3)

        publisher.subscribe(object : Subscriber<Int> {
            override fun onSubscribe(s: Subscription) {
                s.request(Long.MAX_VALUE)
            }

            override fun onNext(t: Int) {
                receivedValues.add(t)
                latch.countDown()
            }

            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })

        signal.value = 2
        signal.value = 3

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(receivedValues).containsExactly(1, 2, 3)
    }

    @Test
    fun `asPublisher respects backpressure`() {
        val signal = mutableSignalOf(1)
        val publisher = signal.asReactiveStreamsPublisher()

        val receivedValues = mutableListOf<Int>()
        val subscriptionRef = AtomicReference<Subscription>()

        publisher.subscribe(object : Subscriber<Int> {
            override fun onSubscribe(s: Subscription) {
                subscriptionRef.set(s)
                s.request(1) // Request only 1
            }

            override fun onNext(t: Int) {
                receivedValues.add(t)
            }

            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })

        // First value should be received
        Thread.sleep(50)
        assertThat(receivedValues).containsExactly(1)

        // Change value but no more requested
        signal.value = 2
        Thread.sleep(50)
        assertThat(receivedValues).containsExactly(1) // Still only 1

        // Request more
        subscriptionRef.get().request(1)
        signal.value = 3
        Thread.sleep(50)
        assertThat(receivedValues).contains(3)
    }

    @Test
    fun `Publisher asSignal reads value`() {
        val publisher = simplePublisher(42)
        val signal = publisher.asSignal(initial = 0)

        Thread.sleep(50) // Wait for publisher to emit
        assertThat(signal.value).isEqualTo(42)
    }

    @Test
    fun `Publisher asSignal with initial value`() {
        val publisher = delayedSinglePublisher(100, 100) // Delayed emission
        val signal = publisher.asSignal(initial = 0)

        // Initial value is available immediately (before publisher emits)
        assertThat(signal.value).isEqualTo(0)

        Thread.sleep(200) // Wait for publisher to emit
        assertThat(signal.value).isEqualTo(100)
    }

    @Test
    fun `Publisher asSignal notifies subscribers`() {
        val publisher = delayedPublisher(listOf(1, 2, 3))
        val signal = publisher.asSignal(initial = 0)

        val values = mutableListOf<Int>()
        signal.subscribe { either ->
            either.fold({}, { values.add(it) })
        }

        Thread.sleep(200)
        assertThat(values).contains(0, 1, 2, 3)
    }

    private fun <T> simplePublisher(value: T): Publisher<T> = Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
            override fun request(n: Long) {
                subscriber.onNext(value)
            }
            override fun cancel() {}
        })
    }

    private fun <T> delayedPublisher(values: List<T>): Publisher<T> = Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
            override fun request(n: Long) {
                Thread {
                    values.forEach { value ->
                        Thread.sleep(20)
                        subscriber.onNext(value)
                    }
                }.start()
            }
            override fun cancel() {}
        })
    }

    private fun <T> delayedSinglePublisher(value: T, delayMs: Long): Publisher<T> = Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
            override fun request(n: Long) {
                Thread {
                    Thread.sleep(delayMs)
                    subscriber.onNext(value)
                }.start()
            }
            override fun cancel() {}
        })
    }
}
