package com.github.fenrur.signal

import com.github.fenrur.signal.impl.CopyOnWriteArrayList
import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.operators.*
import kotlin.concurrent.atomics.*
import kotlin.test.*
import kotlin.time.TimeSource

/**
 * Stress threading tests for the Signal library.
 * Tests high concurrency scenarios with deep chains and wide graphs.
 */
@OptIn(ExperimentalAtomicApi::class)
class StressThreadingTest {

    // =========================================================================
    // DEEP SIGNAL CHAINS WITH CONCURRENT UPDATES
    // =========================================================================

    @Test
    fun `deep chain with concurrent updates`() = repeat(3) {
        val source = DefaultMutableSignal(0)

        var chain: Signal<Int> = source
        repeat(50) {
            chain = chain.map { it + 1 }
        }

        val emissions = CopyOnWriteArrayList<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(30000)
                repeat(25) { i ->
                    source.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(30000))

        // Chain should still be functional
        source.value = 100
        testSleep(100)
        assertEquals(150, chain.value)  // 100 + 50
    }

    // =========================================================================
    // WIDE SIGNAL GRAPHS WITH CONCURRENT UPDATES
    // =========================================================================

    @Test
    fun `wide graph with concurrent updates`() = repeat(3) {
        val sources = List(20) { i -> DefaultMutableSignal(i) }
        val combined = sources.combineAll()

        val emissions = CopyOnWriteArrayList<List<Int>>()
        combined.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(20)

        sources.forEachIndexed { idx, signal ->
            testThread {
                startLatch.await(30000)
                repeat(10) { i ->
                    signal.value = idx * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(30000))

        // Combined signal should still be functional
        sources[0].value = 999
        testSleep(100)
        assertEquals(999, combined.value[0])
    }

    // =========================================================================
    // HIGH THROUGHPUT SCENARIOS
    // =========================================================================

    @Test
    fun `high throughput - 10000 updates single signal`() = repeat(3) {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val updateCount = AtomicInt(0)
        mapped.subscribe { r ->
            r.onSuccess { updateCount.incrementAndFetch() }
        }

        val mark = TimeSource.Monotonic.markNow()

        repeat(10000) { i ->
            source.value = i
        }

        val duration = mark.elapsedNow().inWholeMilliseconds

        // Should complete in reasonable time (< 5 seconds)
        assertTrue(duration < 5000)

        // Final value should be correct
        assertEquals(19998, mapped.value)  // 9999 * 2
    }

    @Test
    fun `high throughput - concurrent updates with multiple readers`() = repeat(3) {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val readCount = AtomicLong(0)
        val writeCount = AtomicLong(0)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(8)

        // 4 writers
        repeat(4) {
            testThread {
                startLatch.await(30000)
                repeat(1000) { i ->
                    source.value = i
                    writeCount.incrementAndFetch()
                }
                doneLatch.countDown()
            }
        }

        // 4 readers
        repeat(4) {
            testThread {
                startLatch.await(30000)
                repeat(1000) {
                    mapped.value
                    readCount.incrementAndFetch()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(30000))

        assertEquals(4000L, writeCount.load())
        assertEquals(4000L, readCount.load())

        // Signal should be consistent
        source.value = 50
        assertEquals(100, mapped.value)
    }

    // =========================================================================
    // MANY SUBSCRIBERS WITH CONCURRENT OPERATIONS
    // =========================================================================

    @Test
    fun `many subscribers - concurrent subscribe and unsubscribe`() = repeat(3) {
        val source = DefaultMutableSignal(0)
        val subscribeCount = AtomicInt(0)
        val unsubscribeCount = AtomicInt(0)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(8)

        // 4 threads subscribing and unsubscribing
        repeat(4) {
            testThread {
                startLatch.await(30000)
                repeat(100) {
                    val unsub = source.subscribe { }
                    subscribeCount.incrementAndFetch()
                    unsub()
                    unsubscribeCount.incrementAndFetch()
                }
                doneLatch.countDown()
            }
        }

        // 4 threads updating
        repeat(4) { threadId ->
            testThread {
                startLatch.await(30000)
                repeat(100) { i ->
                    source.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(30000))

        assertEquals(400, subscribeCount.load())
        assertEquals(400, unsubscribeCount.load())

        // Signal should be functional
        source.value = 999
        assertEquals(999, source.value)
    }

    // =========================================================================
    // COMPLEX DIAMOND WITH CONCURRENT UPDATES
    // =========================================================================

    @Test
    fun `complex diamond with concurrent updates`() = repeat(3) {
        val sources = List(5) { i -> DefaultMutableSignal(i) }

        // Create complex interconnections
        val combined1 = combine(sources[0], sources[1]) { a, b -> a + b }
        val combined2 = combine(sources[1], sources[2]) { a, b -> a + b }
        val combined3 = combine(sources[2], sources[3]) { a, b -> a + b }
        val combined4 = combine(sources[3], sources[4]) { a, b -> a + b }

        val final = combine(combined1, combined2, combined3, combined4) { a, b, c, d -> a + b + c + d }

        val emissions = CopyOnWriteArrayList<Int>()
        final.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(5)

        sources.forEachIndexed { idx, signal ->
            testThread {
                startLatch.await(30000)
                repeat(20) { i ->
                    signal.value = idx * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final signal should be consistent
        sources.forEachIndexed { idx, signal ->
            signal.value = idx * 10
        }
        testSleep(100)

        // 0+10 + 10+20 + 20+30 + 30+40 = 10 + 30 + 50 + 70 = 160
        assertEquals(160, final.value)
    }
}
