package io.github.fenrur.signal.reactive

import io.github.fenrur.signal.asReactiveStreamsPublisher
import io.github.fenrur.signal.asSignal
import io.github.fenrur.signal.mutableSignalOf
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

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

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(42, receivedValue.get())
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

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), receivedValues)
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
        assertEquals(listOf(1), receivedValues)

        // Change value but no more requested
        signal.value = 2
        Thread.sleep(50)
        assertEquals(listOf(1), receivedValues) // Still only 1

        // Request more
        subscriptionRef.get().request(1)
        signal.value = 3
        Thread.sleep(50)
        assertTrue(receivedValues.contains(3))
    }

    @Test
    fun `Publisher asSignal reads value`() {
        val publisher = simplePublisher(42)
        val signal = publisher.asSignal(initial = 0)

        // Subscribe to trigger lazy subscription to publisher
        val values = mutableListOf<Int>()
        signal.subscribe { it.onSuccess { v -> values.add(v) } }

        Thread.sleep(50) // Wait for publisher to emit
        assertEquals(42, signal.value)
    }

    @Test
    fun `Publisher asSignal with initial value`() {
        val publisher = delayedSinglePublisher(100, 100) // Delayed emission
        val signal = publisher.asSignal(initial = 0)

        // Initial value is available immediately (before publisher emits)
        assertEquals(0, signal.value)

        // Subscribe to trigger lazy subscription to publisher
        val values = mutableListOf<Int>()
        signal.subscribe { it.onSuccess { v -> values.add(v) } }

        Thread.sleep(200) // Wait for publisher to emit
        assertEquals(100, signal.value)
    }

    @Test
    fun `Publisher asSignal notifies subscribers`() {
        val publisher = delayedPublisher(listOf(1, 2, 3))
        val signal = publisher.asSignal(initial = 0)

        val values = mutableListOf<Int>()
        signal.subscribe {
            it.onSuccess { v -> values.add(v) }
        }

        Thread.sleep(200)
        assertTrue(values.contains(0))
        assertTrue(values.contains(1))
        assertTrue(values.contains(2))
        assertTrue(values.contains(3))
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
