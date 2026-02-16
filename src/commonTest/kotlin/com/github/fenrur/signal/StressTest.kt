package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.*
import kotlin.test.*

/**
 * Stress tests for the Signal library.
 * Tests large-scale usage, deep chains, and high throughput scenarios.
 */
class StressTest {

    // =========================================================================
    // DEEP SIGNAL CHAINS (100+ LEVELS)
    // =========================================================================

    @Test
    fun `deep signal chain - 100 map operators`() {
        val source = DefaultMutableSignal(0)

        var chain: Signal<Int> = source
        repeat(100) {
            chain = chain.map { it + 1 }
        }

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        // Initial value: 0 + 100 = 100
        assertEquals(100, chain.value)

        emissions.clear()

        // Update source
        source.value = 10
        assertEquals(110, chain.value)
        assertTrue(emissions.contains(110))
    }

    @Test
    fun `deep signal chain - 50 mixed operators`() {
        val source = DefaultMutableSignal(1)

        var chain: Signal<Int> = source
        repeat(50) { i ->
            chain = when (i % 3) {
                0 -> chain.map { it + 1 }
                1 -> chain.filter { true }  // Pass through
                else -> chain.map { it * 1 }  // Identity
            }
        }

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        source.value = 5
        // Each map { it + 1 } adds 1, there are 17 of them (50/3 rounded)
        assertTrue(chain.value > 5)
        assertTrue(emissions.isNotEmpty())
    }

    // =========================================================================
    // WIDE SIGNAL GRAPHS (MANY BRANCHES)
    // =========================================================================

    @Test
    fun `wide graph - 50 derived signals from single source`() {
        val source = DefaultMutableSignal(0)

        val derived = List(50) { i ->
            source.map { it + i }
        }

        val emissions = List(50) { mutableListOf<Int>() }
        derived.forEachIndexed { idx, signal ->
            signal.subscribe { r -> r.onSuccess { emissions[idx].add(it) } }
        }

        // Clear initial emissions
        emissions.forEach { it.clear() }

        // Update source
        source.value = 100

        // All derived signals should update
        derived.forEachIndexed { idx, signal ->
            assertEquals(100 + idx, signal.value)
            assertTrue(emissions[idx].contains(100 + idx))
        }
    }

    @Test
    fun `wide graph - combine many signals`() {
        val signals = List(20) { i -> DefaultMutableSignal(i) }
        val combined = signals.combineAll()

        val emissions = mutableListOf<List<Int>>()
        combined.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        // Update each signal
        signals.forEachIndexed { idx, signal ->
            signal.value = idx * 10
        }

        // Final value should reflect all updates
        assertEquals(20, combined.value.size)
        combined.value.forEachIndexed { idx, value ->
            assertEquals(idx * 10, value)
        }
    }

    // =========================================================================
    // LARGE BATCH OPERATIONS
    // =========================================================================

    @Test
    fun `large batch - 1000 updates in single batch`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val emissions = mutableListOf<Int>()
        mapped.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        batch {
            repeat(1000) { i ->
                source.value = i
            }
        }

        // Only final value should be emitted
        assertEquals(1, emissions.size)
        assertEquals(1998, emissions[0])  // 999 * 2
    }

    @Test
    fun `large batch with combined signals`() {
        val signals = List(10) { i -> DefaultMutableSignal(i) }
        val combined = signals.combineAll()

        val emissions = mutableListOf<List<Int>>()
        combined.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        batch {
            repeat(100) { iteration ->
                signals.forEachIndexed { idx, signal ->
                    signal.value = iteration * 10 + idx
                }
            }
        }

        // Only final combined value
        assertEquals(1, emissions.size)
        assertEquals(10, emissions[0].size)
    }

    // =========================================================================
    // MANY SUBSCRIBERS
    // =========================================================================

    @Test
    fun `many subscribers - 100 listeners on single signal`() {
        val source = DefaultMutableSignal(0)

        val emissions = List(100) { mutableListOf<Int>() }
        val unsubscribers = List(100) { idx ->
            source.subscribe { r -> r.onSuccess { emissions[idx].add(it) } }
        }

        emissions.forEach { it.clear() }

        source.value = 42

        // All listeners should receive the update
        emissions.forEach { list ->
            assertTrue(list.contains(42))
        }

        // Unsubscribe all
        unsubscribers.forEach { it.invoke() }

        // No more emissions after unsubscribe
        emissions.forEach { it.clear() }
        source.value = 100

        emissions.forEach { list ->
            assertTrue(list.isEmpty())
        }
    }

    // =========================================================================
    // COMPLEX DIAMOND PATTERNS
    // =========================================================================

    @Test
    fun `complex diamond - 10 level deep with multiple branches`() {
        //           source
        //          /  |  \
        //        a1  a2  a3
        //        |\ /|\ /|
        //        b1 b2 b3
        //         \ | /
        //          final

        val source = DefaultMutableSignal(1)

        val level1 = List(3) { i -> source.map { it + i } }
        val level2 = List(3) { i ->
            combine(level1[i], level1[(i + 1) % 3]) { a, b -> a + b }
        }
        val final = level2.combineAll().map { it.sum() }

        val emissions = mutableListOf<Int>()
        final.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        source.value = 10

        // Single emission with consistent value
        assertEquals(1, emissions.size)
    }

    // =========================================================================
    // MEMORY PRESSURE SCENARIOS
    // =========================================================================

    @Test
    fun `create and dispose many signals`() {
        repeat(1000) { iteration ->
            val source = DefaultMutableSignal(iteration)
            val mapped = source.map { it * 2 }
            val filtered = mapped.filter { it > 0 }
            val scanned = filtered.scan(0) { acc, v -> acc + v }

            val unsub = scanned.subscribe { }
            source.value = iteration + 1
            unsub()
            scanned.close()
        }

        // Should complete without OutOfMemoryError
    }

    @Test
    fun `create and dispose deep chains`() {
        repeat(100) { iteration ->
            val source = DefaultMutableSignal(iteration)

            var chain: Signal<Int> = source
            repeat(50) {
                chain = chain.map { it + 1 }
            }

            val unsub = chain.subscribe { }
            source.value = iteration + 1
            unsub()
            chain.close()
        }

        // Should complete without issues
    }
}
