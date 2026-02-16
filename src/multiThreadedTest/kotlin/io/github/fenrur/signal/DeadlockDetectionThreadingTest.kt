package io.github.fenrur.signal

import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.impl.batch
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.flatten
import io.github.fenrur.signal.operators.map
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Threading tests for deadlock detection and prevention.
 * Verifies that no deadlock conditions can occur under concurrent operations.
 */
@OptIn(ExperimentalAtomicApi::class)
class DeadlockDetectionThreadingTest {

    // =========================================================================
    // NO DEADLOCK UNDER CONCURRENT OPERATIONS
    // =========================================================================

    @Test
    fun `no deadlock with concurrent subscribe and update`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Subscribe/unsubscribe thread
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) {
                    val unsub = mapped.subscribe { }
                    unsub()
                }
                doneLatch.countDown()
            }
        }

        // Update thread
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    source.value = i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)
    }

    @Test
    fun `no deadlock with concurrent batch operations`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    batch {
                        source.value = i
                        source.value = i + 1
                    }
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)
    }

    @Test
    fun `no deadlock with nested signal access during update`() = repeat(5) {
        val inner = DefaultMutableSignal(0)
        val outer = DefaultMutableSignal<Signal<Int>>(inner)
        val flattened = outer.flatten()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Inner updates
        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    inner.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        // Flattened reads
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(50) {
                    flattened.value
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)
    }

    @Test
    fun `no deadlock with listener modifying source`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val updates = AtomicInt(0)

        // Listener that modifies the source (recursive update)
        source.subscribe { r ->
            r.onSuccess { v ->
                if (v < 5) {
                    // This triggers a recursive update
                    source.update { it + 1 }
                }
                updates.incrementAndFetch()
            }
        }

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(30000)
                repeat(10) {
                    source.value = 0
                    testSleep(10)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(30000)

        assertTrue(finished)
    }

    // =========================================================================
    // COMPLEX CONCURRENT SCENARIOS
    // =========================================================================

    @Test
    fun `no deadlock with combined signal diamond and concurrent updates`() = repeat(5) {
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = a.map { it * 3 }
        val d = combine(b, c) { x, y -> x + y }

        val emissions = CopyOnWriteArrayList<Int>()
        d.subscribe { r -> r.onSuccess { emissions.add(it) } }

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    a.value = i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)

        // Verify consistency
        a.value = 10
        testSleep(50)
        assertEquals(50, d.value)  // 10*2 + 10*3
    }

    @Test
    fun `no deadlock with rebinding during updates`() = repeat(5) {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(100)
        val bindable = bindableSignalOf(source1)

        val emissions = CopyOnWriteArrayList<Int>()
        bindable.subscribe { r -> r.onSuccess { emissions.add(it) } }

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Source1 updates
        testThread {
            startLatch.await(10000)
            repeat(50) { i ->
                source1.value = i
            }
            doneLatch.countDown()
        }

        // Source2 updates
        testThread {
            startLatch.await(10000)
            repeat(50) { i ->
                source2.value = i + 100
            }
            doneLatch.countDown()
        }

        // Rebinding thread
        testThread {
            startLatch.await(10000)
            repeat(25) {
                bindable.bindTo(source1)
                testSleep(1)
                bindable.bindTo(source2)
                testSleep(1)
            }
            doneLatch.countDown()
        }

        // Reader thread
        testThread {
            startLatch.await(10000)
            repeat(100) {
                bindable.value
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)
    }

    // =========================================================================
    // CONCURRENT BINDING SCENARIOS
    // =========================================================================

    @Test
    fun `concurrent rebinding is safe`() = repeat(5) {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val source3 = DefaultMutableSignal(3)
        val bindable = bindableSignalOf(source1)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(3)

        testThread {
            startLatch.await(10000)
            repeat(50) {
                bindable.bindTo(source1)
            }
            doneLatch.countDown()
        }

        testThread {
            startLatch.await(10000)
            repeat(50) {
                bindable.bindTo(source2)
            }
            doneLatch.countDown()
        }

        testThread {
            startLatch.await(10000)
            repeat(50) {
                bindable.bindTo(source3)
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)

        // Bindable should be bound to one of the sources
        val value = bindable.value
        assertTrue(value == 1 || value == 2 || value == 3)
    }

    @Test
    fun `concurrent read during rebind is safe`() = repeat(5) {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val bindable = bindableSignalOf(source1)

        val values = CopyOnWriteArrayList<Int>()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Rebind threads
        repeat(2) { idx ->
            testThread {
                startLatch.await(10000)
                repeat(100) {
                    bindable.bindTo(if (idx == 0) source1 else source2)
                }
                doneLatch.countDown()
            }
        }

        // Read threads
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) {
                    values.add(bindable.value)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10000)

        assertTrue(finished)

        // All values should be either 1 or 2
        assertTrue(values.all { it == 1 || it == 2 })
    }
}
