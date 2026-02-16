package io.github.fenrur.signal

import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import io.github.fenrur.signal.operators.*
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Threading tests for operator composition - chaining multiple operators together.
 * Verifies that complex operator chains work correctly under concurrent conditions.
 */
@OptIn(ExperimentalAtomicApi::class)
class OperatorCompositionThreadingTest {

    // =========================================================================
    // CONCURRENT OPERATOR CHAINS
    // =========================================================================

    @Test
    fun `concurrent - map-filter-scan chain thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val chain = source
            .map { it * 2 }
            .filter { it >= 0 }
            .scan(0) { acc, v -> acc + v }

        val emissions = CopyOnWriteArrayList<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(30000)
                repeat(50) { i ->
                    source.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Chain should still be functional
        val before = chain.value
        source.value = 10
        testSleep(50)
        assertEquals(before + 20, chain.value)
    }

    @Test
    fun `concurrent - combine-flatMap chain thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val inner = DefaultMutableSignal(100)

        val chain = combine(a, b) { x, y -> x + y }
            .flatMap { sum -> inner.map { it + sum } }

        val emissions = CopyOnWriteArrayList<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        testThread {
            startLatch.await(30000)
            repeat(50) { i -> a.value = i }
            doneLatch.countDown()
        }

        testThread {
            startLatch.await(30000)
            repeat(50) { i -> b.value = i * 10 }
            doneLatch.countDown()
        }

        testThread {
            startLatch.await(30000)
            repeat(50) { i -> inner.value = i * 100 }
            doneLatch.countDown()
        }

        testThread {
            startLatch.await(30000)
            repeat(50) { chain.value }  // Concurrent reads
            doneLatch.countDown()
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Chain should still be functional
        a.value = 5
        b.value = 15
        inner.value = 200
        testSleep(50)
        assertEquals(220, chain.value)  // 200 + (5 + 15)
    }

    @Test
    fun `concurrent - deep composition chain thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(0)

        // Create a complex chain
        val chain = source
            .map { it + 1 }
            .filter { it > 0 }
            .map { it * 2 }
            .distinctUntilChangedBy { it % 10 }
            .scan(0) { acc, v -> acc + v }
            .map { it.toString() }
            .filter { it.length < 10 }

        val emissions = CopyOnWriteArrayList<String>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(30000)
                repeat(50) { i ->
                    source.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Chain should still be functional
        assertNotNull(chain.value)
    }
}
