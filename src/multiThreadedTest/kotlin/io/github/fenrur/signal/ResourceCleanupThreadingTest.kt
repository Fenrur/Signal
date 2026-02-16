package io.github.fenrur.signal

import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.operators.*
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Threading tests for resource cleanup, subscription management.
 * Verifies thread-safety of subscribe/unsubscribe and close operations.
 */
@OptIn(ExperimentalAtomicApi::class)
class ResourceCleanupThreadingTest {

    // =========================================================================
    // CONCURRENT SUBSCRIBE/UNSUBSCRIBE
    // =========================================================================

    @Test
    fun `concurrent subscribe and unsubscribe is thread-safe`() = repeat(10) {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }
        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(16)

        // Threads that subscribe and immediately unsubscribe
        repeat(10) {
            testThread {
                startLatch.await(30000)
                repeat(50) {
                    val unsub = mapped.subscribe { }
                    unsub()
                }
                doneLatch.countDown()
            }
        }

        // Threads that also subscribe and unsubscribe (replaces the old unsubscribe-from-queue threads)
        repeat(5) {
            testThread {
                startLatch.await(30000)
                repeat(100) {
                    val unsub = mapped.subscribe { }
                    unsub()
                }
                doneLatch.countDown()
            }
        }

        // Thread that updates
        testThread {
            startLatch.await(30000)
            repeat(100) { i ->
                source.value = i
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5000))

        // Signal still works
        source.value = 999
        assertEquals(1998, mapped.value)
    }

    @Test
    fun `concurrent close is thread-safe`() = repeat(10) {
        val source = DefaultMutableSignal(0)
        val signals = (1..10).map { source.map { v -> v * it } }
        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(11)

        // Subscribe to all
        signals.forEach { signal ->
            signal.subscribe { }
        }

        // Concurrent close
        signals.forEach { signal ->
            testThread {
                startLatch.await(30000)
                signal.close()
                doneLatch.countDown()
            }
        }

        // Concurrent value updates
        testThread {
            startLatch.await(30000)
            repeat(100) { i ->
                source.value = i
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5000))

        // All signals are closed
        signals.forEach { assertTrue(it.isClosed) }
    }
}
