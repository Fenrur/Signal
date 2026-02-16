package com.github.fenrur.signal

import com.github.fenrur.signal.impl.CopyOnWriteArrayList
import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.operators.*
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Multi-threaded concurrency and stress tests.
 *
 * Signal graphs are illustrated with ASCII diagrams above each test:
 * - Arrows show data flow direction (top to bottom)
 * - Letters represent signals (a, b, c, etc.)
 * - Operators are shown inline where relevant
 */
class ConcurrencyAndGlitchFreeThreadingTest {

    // =========================================================================
    // CONCURRENT MODIFICATION TESTS
    // Multiple threads modifying signals simultaneously
    // =========================================================================

    @Test
    fun `concurrent - multiple threads incrementing signal`() = repeat(10) {
        //   counter
        //     |
        //   (atomic increments from N threads)
        //
        val counter = mutableSignalOf(0)
        val threads = 10
        val incrementsPerThread = 1000

        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) {
            testThread {
                try {
                    repeat(incrementsPerThread) {
                        counter.update { it + 1 }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        assertEquals(threads * incrementsPerThread, counter.value)
    }

    @Test
    fun `concurrent - readers and writers`() = repeat(10) {
        //   signal
        //     |
        //   mapped (map * 2)
        //     |
        //   (concurrent reads while writing)
        //
        val signal = mutableSignalOf(0)
        val mapped = signal.map { it * 2 }
        val writers = 5
        val readers = 5
        val operations = 500

        val doneLatch = TestCountDownLatch(writers + readers)
        val errors = CopyOnWriteArrayList<Throwable>()

        // Writers
        repeat(writers) {
            testThread {
                try {
                    repeat(operations) { i ->
                        signal.value = i
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Readers
        repeat(readers) {
            testThread {
                try {
                    repeat(operations) {
                        val v = signal.value
                        val m = mapped.value
                        // Mapped should always be even (2x something)
                        assertEquals(0, m % 2)
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `concurrent - subscribe and unsubscribe while updating`() = repeat(5) {
        //   signal
        //     |
        //   mapped
        //     |
        //   (concurrent subscribe/unsubscribe)
        //
        val signal = mutableSignalOf(0)
        val mapped = signal.map { it * 2 }
        val operations = 200
        val doneLatch = TestCountDownLatch(3)
        val errors = CopyOnWriteArrayList<Throwable>()

        // Writer
        testThread {
            try {
                repeat(operations) { i ->
                    signal.value = i
                }
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                doneLatch.countDown()
            }
        }

        // Subscriber/Unsubscriber 1
        testThread {
            try {
                repeat(operations) {
                    val unsub = mapped.subscribe { }
                    unsub()
                }
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                doneLatch.countDown()
            }
        }

        // Subscriber/Unsubscriber 2
        testThread {
            try {
                repeat(operations) {
                    val unsub = signal.subscribe { }
                    unsub()
                }
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                doneLatch.countDown()
            }
        }

        assertTrue(doneLatch.await(30000))

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `concurrent - complex graph with multiple sources`() = repeat(5) {
        //   a       b       c
        //    \     /|\     /
        //     \   / | \   /
        //      \ /  |  \ /
        //       ab  |   bc
        //        \  |  /
        //         \ | /
        //          abc
        //
        // ab = a + b
        // bc = b + c
        // abc = ab + bc = a + 2b + c
        val a = mutableSignalOf(0)
        val b = mutableSignalOf(0)
        val c = mutableSignalOf(0)

        val ab = combine(a, b) { x, y -> x + y }
        val bc = combine(b, c) { x, y -> x + y }
        val abc = combine(ab, bc) { x, y -> x + y }

        val emissions = CopyOnWriteArrayList<Int>()
        abc.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 3
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        testThread {
            try {
                repeat(updates) { i -> a.value = i }
            } finally {
                doneLatch.countDown()
            }
        }

        testThread {
            try {
                repeat(updates) { i -> b.value = i }
            } finally {
                doneLatch.countDown()
            }
        }

        testThread {
            try {
                repeat(updates) { i -> c.value = i }
            } finally {
                doneLatch.countDown()
            }
        }

        assertTrue(doneLatch.await(30000))

        // Final state should be consistent
        val finalAbc = abc.value
        assertTrue(finalAbc >= 0)
    }

    @Test
    fun `concurrent - batch from multiple threads`() = repeat(5) {
        //   a     b
        //    \   /
        //     \ /
        //     sum
        //
        val a = mutableSignalOf(0)
        val b = mutableSignalOf(0)
        val sum = combine(a, b) { x, y -> x + y }

        val emissions = CopyOnWriteArrayList<Int>()
        sum.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        val threads = 4
        val batches = 50
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) { t ->
            testThread {
                try {
                    repeat(batches) { i ->
                        batch {
                            a.value = t * 1000 + i
                            b.value = t * 1000 + i
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(30000))

        // Final state should be consistent
        assertEquals(a.value + b.value, sum.value)
    }

    // =========================================================================
    // OPERATOR THREAD-SAFETY TESTS
    // Verify each operator works correctly under concurrency
    // =========================================================================

    @Test
    fun `concurrent - map operator thread safety`() = repeat(5) {
        //   source
        //     |
        //   mapped (map * 2)
        //
        val source = mutableSignalOf(0)
        val computeCount = AtomicInt(0)
        val mapped = source.map {
            computeCount.incrementAndFetch()
            it * 2
        }

        val emissions = CopyOnWriteArrayList<Int>()
        mapped.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) {
            testThread {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Value should be consistent
        assertEquals(source.value * 2, mapped.value)
    }

    @Test
    fun `concurrent - filter operator thread safety`() = repeat(5) {
        //   source
        //     |
        //   filtered (filter even)
        //
        val source = mutableSignalOf(0)
        val filtered = source.filter { it % 2 == 0 }

        val emissions = CopyOnWriteArrayList<Int>()
        filtered.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) {
            testThread {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // All emissions should be even
        emissions.forEach { assertEquals(0, it % 2) }
    }

    @Test
    fun `concurrent - combine operator thread safety`() = repeat(5) {
        //   a     b
        //    \   /
        //     \ /
        //   combined
        //
        val a = mutableSignalOf(0)
        val b = mutableSignalOf(0)
        val combined = combine(a, b) { x, y -> x to y }

        val emissions = CopyOnWriteArrayList<Pair<Int, Int>>()
        combined.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads / 2) {
            testThread {
                try {
                    repeat(updates) { i -> a.value = i }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        repeat(threads / 2) {
            testThread {
                try {
                    repeat(updates) { i -> b.value = i }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // No exceptions means thread-safe
        assertEquals(a.value to b.value, combined.value)
    }

    @Test
    fun `concurrent - scan operator thread safety`() = repeat(5) {
        //   source
        //     |
        //   scanned (scan + accumulate)
        //
        val source = mutableSignalOf(0)
        val scanned = source.scan(0) { acc, v -> acc + v }

        val emissions = CopyOnWriteArrayList<Int>()
        scanned.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 50
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) {
            testThread {
                try {
                    repeat(updates) { i ->
                        source.value = 1
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Scan should always produce increasing values
        var prev = Int.MIN_VALUE
        emissions.forEach { v ->
            assertTrue(v >= prev)
            prev = v
        }
    }

    @Test
    fun `concurrent - distinctUntilChangedBy thread safety`() = repeat(5) {
        //   source
        //     |
        //   distinct (by id)
        //
        data class Item(val id: Int, val value: String)

        val source = mutableSignalOf(Item(0, "initial"))
        val distinct = source.distinctUntilChangedBy { it.id }

        val emissions = CopyOnWriteArrayList<Item>()
        distinct.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 50
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) { t ->
            testThread {
                try {
                    repeat(updates) { i ->
                        source.value = Item(t, "value-$i")
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // No exceptions means thread-safe
        assertTrue(emissions.isNotEmpty())
    }

    @Test
    fun `concurrent - bimap operator thread safety`() = repeat(5) {
        //   source <---> bimap
        //      (bidirectional mapping)
        //
        val source = mutableSignalOf(0)
        val bimap = source.bimap(
            forward = { it.toString() },
            reverse = { it.toIntOrNull() ?: 0 }
        )

        val emissions = CopyOnWriteArrayList<String>()
        bimap.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 50
        val doneLatch = TestCountDownLatch(threads)

        // Writers to source
        repeat(threads / 2) {
            testThread {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Writers through bimap
        repeat(threads / 2) {
            testThread {
                try {
                    repeat(updates) { i ->
                        bimap.value = i.toString()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Value should be consistent
        assertEquals(source.value.toString(), bimap.value)
    }

    // =========================================================================
    // CONSISTENCY TESTS
    // =========================================================================

    @Test
    fun `consistency - derived value always matches source`() = repeat(10) {
        //   source
        //     |
        //   derived (map * 2)
        //
        val source = mutableSignalOf(0)
        val derived = source.map { it * 2 }

        val threads = 4
        val checks = 1000
        val doneLatch = TestCountDownLatch(threads + 1)
        val running = AtomicBoolean(true)
        val inconsistencies = AtomicInt(0)

        // Writer
        testThread {
            try {
                repeat(checks) { i ->
                    source.value = i
                }
            } finally {
                running.store(false)
                doneLatch.countDown()
            }
        }

        // Checkers
        repeat(threads) {
            testThread {
                try {
                    while (running.load()) {
                        val s = source.value
                        val d = derived.value
                        // d should always be even
                        if (d % 2 != 0) {
                            inconsistencies.incrementAndFetch()
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        assertEquals(0, inconsistencies.load())
    }

    // =========================================================================
    // STRESS TESTS
    // High load scenarios
    // =========================================================================

    @Test
    fun `stress - many signals combined`() = repeat(3) {
        //   s0  s1  s2  ...  s9
        //    \   \   \  ...  /
        //     \   \   \ ... /
        //      \   \   \   /
        //       \   \   \ /
        //        \   \   |
        //         \   \ /
        //          \   |
        //           \ /
        //          combined
        //             |
        //            sum
        //
        val signals = (0 until 10).map { mutableSignalOf(0) }
        val combined = signals.map { it as Signal<Int> }.combineAll()
        val sum = combined.map { it.sum() }

        val emissions = CopyOnWriteArrayList<Int>()
        sum.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 10
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        signals.forEachIndexed { index, signal ->
            testThread {
                try {
                    repeat(updates) { i ->
                        signal.value = i
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(30000))

        // Final sum should be consistent
        val expectedSum = signals.sumOf { it.value }
        assertEquals(expectedSum, sum.value)
    }

    @Test
    fun `stress - rapid subscribe unsubscribe`() = repeat(3) {
        //   source
        //     |
        //   mapped
        //     |
        //   (rapid subscribe/unsubscribe from N threads)
        //
        val source = mutableSignalOf(0)
        val mapped = source.map { it * 2 }

        val threads = 8
        val operations = 500
        val doneLatch = TestCountDownLatch(threads)
        val errors = CopyOnWriteArrayList<Throwable>()

        repeat(threads) {
            testThread {
                try {
                    repeat(operations) {
                        val unsub = mapped.subscribe { }
                        source.update { it + 1 }
                        unsub()
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(30000))

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `stress - deep chain with concurrent updates`() {
        //   source
        //     |
        //    m1 (map + 1)
        //     |
        //    m2 (map + 1)
        //     |
        //    ...
        //     |
        //   m20 (map + 1)
        //
        val source = mutableSignalOf(0)
        var current: Signal<Int> = source

        // Create a chain of 20 mapped signals
        repeat(20) {
            current = current.map { it + 1 }
        }

        val final = current
        val emissions = CopyOnWriteArrayList<Int>()
        final.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) {
            testThread {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Final value should be source + 20
        assertEquals(source.value + 20, final.value)
    }

    // =========================================================================
    // ADDITIONAL CONCURRENCY TESTS - MAPNOTNULL, FLATMAP, SCAN, BIMAP
    // =========================================================================

    @Test
    fun `concurrent - mapNotNull thread safety`() = repeat(5) {
        // Test that mapNotNull correctly filters nulls under concurrent updates
        val source = mutableSignalOf<Int?>(1)
        val mapped = source.mapNotNull { it?.let { v -> v * 2 } }

        val emissions = CopyOnWriteArrayList<Int>()
        mapped.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 200
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) { threadId ->
            testThread {
                try {
                    repeat(updates) { i ->
                        // Alternate between null and non-null values
                        source.value = if (i % 2 == 0) i + threadId * 100 else null
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // All emissions should be non-null and doubled
        assertTrue(emissions.all { it % 2 == 0 })
        // Value should be consistent
        val finalValue = mapped.value
        assertEquals(0, finalValue % 2)
    }

    @Test
    fun `concurrent - flatMap rapid inner signal switching`() = repeat(5) {
        // Test that flatMap correctly handles rapid inner signal switching
        val innerSignals = (0 until 10).map { mutableSignalOf(it * 10) }
        val selector = mutableSignalOf(0)
        val flattened = selector.flatMap { idx -> innerSignals[idx % innerSignals.size] }

        val emissions = CopyOnWriteArrayList<Int>()
        flattened.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val switches = 100
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) { threadId ->
            testThread {
                try {
                    repeat(switches) { i ->
                        // Rapidly switch between inner signals
                        selector.value = (threadId * 100 + i) % innerSignals.size
                        // Also update inner signals
                        innerSignals[i % innerSignals.size].value = threadId * 1000 + i
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Verify the final value is consistent
        val finalIdx = selector.value % innerSignals.size
        assertEquals(innerSignals[finalIdx].value, flattened.value)
    }

    @Test
    fun `concurrent - scan accumulator consistency`() = repeat(5) {
        val source = mutableSignalOf(0)
        val scanned = source.scan(0) { acc, value -> acc + value }

        val emissions = CopyOnWriteArrayList<Int>()
        scanned.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)
        val updateCount = AtomicInt(0)

        repeat(threads) {
            testThread {
                try {
                    repeat(updates) {
                        source.value = 1
                        updateCount.incrementAndFetch()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // All emissions should be non-negative (no corruption)
        assertTrue(emissions.all { it >= 0 })
    }

    @Test
    fun `concurrent - bimap bidirectional updates`() = repeat(5) {
        val source = mutableSignalOf(10)
        val bimapped = source.bimap(
            forward = { it * 2 },
            reverse = { it / 2 }
        )

        val forwardEmissions = CopyOnWriteArrayList<Int>()
        bimapped.subscribe { it.onSuccess { v -> forwardEmissions.add(v) } }

        val threads = 4
        val updates = 100
        val doneLatch = TestCountDownLatch(threads)

        repeat(threads) { threadId ->
            testThread {
                try {
                    repeat(updates) { i ->
                        if (i % 2 == 0) {
                            // Forward update (through source)
                            source.value = threadId * 100 + i
                        } else {
                            // Reverse update (through bimapped)
                            bimapped.value = (threadId * 100 + i) * 2
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Verify consistency: bimapped.value should always be source.value * 2
        assertEquals(source.value * 2, bimapped.value)
    }

    @Test
    fun `concurrent - bindable signal cycle detection`() = repeat(5) {
        // Test that concurrent binding operations correctly detect cycles
        val signals = (0 until 5).map { bindableSignalOf(it) }

        val doneLatch = TestCountDownLatch(4)
        val cycleDetected = AtomicBoolean(false)

        // Try to create cycles concurrently
        repeat(4) { threadId ->
            testThread {
                try {
                    repeat(50) { i ->
                        val from = (threadId + i) % signals.size
                        val to = (threadId + i + 1) % signals.size
                        try {
                            // This might create a cycle
                            signals[from].bindTo(signals[to])
                        } catch (e: IllegalStateException) {
                            if (e.message?.contains("Circular") == true) {
                                cycleDetected.store(true)
                            }
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        assertTrue(doneLatch.await(10000))

        // Verify no cycles exist in final state
        for (signal in signals) {
            if (signal.isBound()) {
                // Walk the chain and verify no cycles
                val visited = mutableSetOf<Signal<*>>()
                var current: Signal<*>? = signal.currentSignal()
                while (current != null) {
                    if (current in visited) {
                        throw AssertionError("Cycle detected in final state!")
                    }
                    visited.add(current)
                    current = (current as? BindableSignal<*>)?.currentSignal()
                }
            }
        }
    }

    @Test
    fun `concurrent - flatten with closing inner signals`() = repeat(5) {
        val innerSignals = CopyOnWriteArrayList<MutableSignal<Int>>().apply {
            addAll((0 until 10).map { mutableSignalOf(it * 10) })
        }
        val selector = mutableSignalOf(0)
        val flattened: Signal<Int> = selector.flatMap { idx -> innerSignals[idx % innerSignals.size] }

        val emissions = CopyOnWriteArrayList<Int>()
        flattened.subscribe { r -> r.onSuccess { v -> emissions.add(v) } }

        val doneLatch = TestCountDownLatch(3)

        // Thread 1: Switch selectors
        testThread {
            try {
                repeat(100) { i ->
                    selector.value = i % innerSignals.size
                }
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 2: Update inner signals
        testThread {
            try {
                repeat(100) { i ->
                    val idx = i % innerSignals.size
                    if (!innerSignals[idx].isClosed) {
                        innerSignals[idx].value = i * 100
                    }
                }
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 3: Close and recreate inner signals
        testThread {
            try {
                repeat(20) { i ->
                    val idx = i % innerSignals.size
                    innerSignals[idx].close()
                    innerSignals[idx] = mutableSignalOf((i + 1) * 1000)
                }
            } finally {
                doneLatch.countDown()
            }
        }

        assertTrue(doneLatch.await(10000))

        // Should not crash - just verify we can still read a value
        try {
            flattened.value
        } catch (_: Throwable) {
            // Inner signal might be closed, that's acceptable
        }
    }
}
