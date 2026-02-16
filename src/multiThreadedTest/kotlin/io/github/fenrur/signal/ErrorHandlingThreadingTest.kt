package io.github.fenrur.signal

import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.operators.map
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Threading tests for error handling and propagation through the signal graph.
 */
@OptIn(ExperimentalAtomicApi::class)
class ErrorHandlingThreadingTest {

    @Test
    fun `concurrent errors do not corrupt signal state`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { v: Int ->
            if (v % 7 == 0 && v != 0) throw RuntimeException("Divisible by 7")
            v * 2
        }

        val successCount = AtomicInt(0)
        val errorCount = AtomicInt(0)
        val threads = mutableListOf<TestThread>()

        // Multiple threads reading
        repeat(5) {
            threads += testThread {
                repeat(100) {
                    try {
                        mapped.value
                        successCount.incrementAndFetch()
                    } catch (e: RuntimeException) {
                        errorCount.incrementAndFetch()
                    }
                }
            }
        }

        // One thread writing
        threads += testThread {
            repeat(50) { i ->
                source.value = i
                testSleep(1)
            }
        }

        threads.forEach { it.join(30000) }

        // Both successes and errors occurred
        assertTrue(successCount.load() > 0)
        // Signal is still functional
        source.value = 10
        assertEquals(20, mapped.value)
    }

    @Test
    fun `concurrent self-unsubscription is thread-safe`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val unsubscribed = AtomicInt(0)
        val received = AtomicInt(0)
        val unsubRefs = CopyOnWriteArrayList<AtomicReference<UnSubscriber?>>()

        // Create 10 listeners
        repeat(10) {
            val ref = AtomicReference<UnSubscriber?>(null)
            unsubRefs.add(ref)
            val unsub = source.subscribe { result ->
                result.onSuccess { v ->
                    received.incrementAndFetch()
                    // Each listener unsubscribes itself randomly
                    if (v > 5 && kotlin.random.Random.nextDouble() > 0.5) {
                        val u = ref.exchange(null)
                        if (u != null) {
                            u.invoke()
                            unsubscribed.incrementAndFetch()
                        }
                    }
                }
            }
            ref.store(unsub)
        }

        // Update values concurrently from multiple threads
        val latch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                try {
                    repeat(20) { i ->
                        source.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10000))

        // Some values were received
        assertTrue(received.load() > 0)
        // Signal is still functional
        source.value = 100
    }
}
