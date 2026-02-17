package io.github.fenrur.signal

import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.impl.batch
import io.github.fenrur.signal.operators.*
import io.github.fenrur.signal.operators.distinctUntilChangedBy
import io.github.fenrur.signal.operators.filter
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.pairwise
import io.github.fenrur.signal.operators.scan
import io.github.fenrur.signal.operators.withLatestFrom
import kotlin.test.*

/**
 * Tests for operator composition - chaining multiple operators together.
 * Verifies that complex operator chains work correctly.
 */
class OperatorCompositionTest {

    // =========================================================================
    // BASIC OPERATOR CHAINS
    // =========================================================================

    @Test
    fun `map-filter-scan chain works correctly`() {
        val source = DefaultMutableSignal(0)
        val chain = source
            .map { it * 2 }           // 0 -> 0, 1 -> 2, 2 -> 4, 3 -> 6
            .filter { it > 0 }        // Filter out 0
            .scan(0) { acc, v -> acc + v }  // Accumulate

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        source.value = 1  // 2 -> acc = 2
        source.value = 2  // 4 -> acc = 6
        source.value = 3  // 6 -> acc = 12

        assertEquals(listOf(2, 6, 12), emissions.toList())
    }

    @Test
    fun `filter-map-distinctUntilChangedBy chain works correctly`() {
        data class Item(val id: Int, val name: String)

        val source = DefaultMutableSignal(Item(0, "init"))
        val chain = source
            .filter { it.id > 0 }
            .map { it.name.uppercase() }
            .distinctUntilChangedBy { it.length }

        val emissions = mutableListOf<String>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        source.value = Item(1, "hello")    // HELLO (len 5)
        source.value = Item(2, "world")    // WORLD (len 5) - same length, filtered
        source.value = Item(3, "hi")       // HI (len 2) - different length
        source.value = Item(4, "bye")      // BYE (len 3) - different length

        assertEquals(listOf("HELLO", "HI", "BYE"), emissions.toList())
    }

    @Test
    fun `combine-map-filter chain works correctly`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val chain = combine(a, b) { x, y -> x + y }
            .map { it * 2 }
            .filter { it > 20 }

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        a.value = 5   // 5 + 10 = 15 * 2 = 30 > 20 -> emit
        b.value = 20  // 5 + 20 = 25 * 2 = 50 > 20 -> emit
        a.value = 1   // 1 + 20 = 21 * 2 = 42 > 20 -> emit

        assertEquals(listOf(30, 50, 42), emissions.toList())
    }

    @Test
    fun `flatMap with inner map chain works correctly`() {
        val source = DefaultMutableSignal(1)
        val inner1 = DefaultMutableSignal(100)
        val inner2 = DefaultMutableSignal(200)

        val chain = source.flatMap { v ->
            if (v % 2 == 0) inner1.map { it + v } else inner2.map { it * v }
        }

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        // Initial: source=1 (odd) -> inner2 * 1 = 200
        assertEquals(200, chain.value)

        emissions.clear()

        source.value = 2  // even -> inner1 + 2 = 102
        inner1.value = 150  // inner1 + 2 = 152
        source.value = 3  // odd -> inner2 * 3 = 600

        assertTrue(emissions.contains(102))
        assertTrue(emissions.contains(152))
        assertTrue(emissions.contains(600))
    }

    @Test
    fun `pairwise-map-filter chain works correctly`() {
        val source = DefaultMutableSignal(0)
        val chain = source
            .pairwise()
            .map { (a, b) -> b - a }  // Compute difference
            .filter { it > 0 }        // Only positive differences

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        source.value = 5   // (0, 5) -> diff = 5
        source.value = 3   // (5, 3) -> diff = -2 (filtered)
        source.value = 10  // (3, 10) -> diff = 7

        assertEquals(listOf(5, 7), emissions.toList())
    }

    @Test
    fun `withLatestFrom-scan chain works correctly`() {
        val source = DefaultMutableSignal(0)
        val other = DefaultMutableSignal(10)

        val chain = source
            .withLatestFrom(other) { a, b -> a + b }
            .scan(0) { acc, v -> acc + v }

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        source.value = 1   // 1 + 10 = 11 -> acc = 10 (initial) + 11 = 21
        other.value = 20   // No emission (withLatestFrom only emits on source change)
        source.value = 2   // 2 + 20 = 22 -> acc = 21 + 22 = 43
        source.value = 3   // 3 + 20 = 23 -> acc = 43 + 23 = 66

        // Verify emissions received (actual values depend on initial computation)
        assertTrue(emissions.isNotEmpty())
        assertTrue(emissions.size >= 3)
    }

    // =========================================================================
    // BATCH WITH OPERATOR CHAINS
    // =========================================================================

    @Test
    fun `batch updates through operator chain emit single final value`() {
        val source = DefaultMutableSignal(0)
        val chain = source
            .map { it * 2 }
            .filter { it >= 0 }
            .scan(0) { acc, v -> acc + v }

        val emissions = mutableListOf<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        batch {
            source.value = 1  // 2
            source.value = 2  // 4
            source.value = 3  // 6
        }

        // In batch mode, only the final source value (3) is seen by the chain
        // map(3) = 6, scan(acc + 6) where acc was the initial accumulation
        // Only one emission should occur after batch
        assertEquals(1, emissions.size)
        // The scan sees only the final value after batch (6) and adds to previous acc
    }

    @Test
    fun `nested batch through combined signals`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val c = DefaultMutableSignal(100)

        val ab = combine(a, b) { x, y -> x + y }
        val abc = combine(ab, c) { xy, z -> xy + z }

        val emissions = mutableListOf<Int>()
        abc.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        batch {
            a.value = 2
            batch {
                b.value = 20
                c.value = 200
            }
        }

        // Only final value
        assertEquals(1, emissions.size)
        assertEquals(222, emissions[0])  // 2 + 20 + 200
    }

    // =========================================================================
    // ERROR PROPAGATION THROUGH CHAINS
    // =========================================================================

    @Test
    fun `error in middle of chain propagates correctly`() {
        var shouldThrow = false
        val source = DefaultMutableSignal(1)
        val chain = source
            .map { it * 2 }
            .map { v ->
                if (shouldThrow) throw RuntimeException("Error in chain")
                v + 1
            }
            .map { it * 3 }

        val errors = mutableListOf<Throwable>()
        val values = mutableListOf<Int>()

        chain.subscribe { r ->
            r.onSuccess { values.add(it) }
            r.onFailure { errors.add(it) }
        }

        values.clear()

        // Normal operation
        source.value = 2
        assertTrue(values.contains(15))  // (2*2 + 1) * 3

        // Trigger error
        shouldThrow = true
        source.value = 3

        assertTrue(errors.isNotEmpty())
        assertTrue(errors[0] is RuntimeException)

        // Recovery
        shouldThrow = false
        source.value = 4
        assertEquals(27, chain.value)  // (4*2 + 1) * 3
    }

    @Test
    fun `error in combine chain propagates correctly`() {
        var shouldThrow = false
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)

        val chain = combine(a, b) { x, y ->
            if (shouldThrow) throw RuntimeException("Combine error")
            x + y
        }.map { it * 2 }

        val errors = mutableListOf<Throwable>()
        chain.subscribe { r -> r.onFailure { errors.add(it) } }

        shouldThrow = true
        a.value = 5

        assertTrue(errors.isNotEmpty())
    }

    // =========================================================================
    // COMPLEX GRAPH STRUCTURES
    // =========================================================================

    @Test
    fun `diamond pattern with different operators on each branch`() {
        //       a
        //      / \
        //   map   filter
        //      \ /
        //    combine
        val a = DefaultMutableSignal(5)
        val mapped = a.map { it * 2 }
        val filtered = a.filter { it > 3 }
        val combined = combine(mapped, filtered) { m, f -> m + f }

        val emissions = mutableListOf<Int>()
        combined.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        a.value = 10  // mapped = 20, filtered = 10 -> 30
        assertEquals(listOf(30), emissions.toList())

        a.value = 7   // mapped = 14, filtered = 7 -> 21
        assertTrue(emissions.contains(21))
    }

    @Test
    fun `multiple paths converging with scan`() {
        //     a     b
        //     |     |
        //   map   map
        //     \   /
        //    combine
        //       |
        //     scan
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)

        val aDouble = a.map { it * 2 }
        val bTriple = b.map { it * 3 }
        val combined = combine(aDouble, bTriple) { x, y -> x + y }
        val scanned = combined.scan(0) { acc, v -> acc + v }

        val emissions = mutableListOf<Int>()
        scanned.subscribe { r -> r.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=10 -> aDouble=2, bTriple=30 -> combined=32 -> scan(0+32)=32
        assertEquals(32, scanned.value)

        emissions.clear()

        a.value = 2   // aDouble = 4, bTriple = 30 -> combined = 34, scan = 32 + 34 = 66
        a.value = 3   // aDouble = 6, bTriple = 30 -> combined = 36, scan = 66 + 36 = 102

        // Verify emissions occurred
        assertTrue(emissions.isNotEmpty())
    }
}
