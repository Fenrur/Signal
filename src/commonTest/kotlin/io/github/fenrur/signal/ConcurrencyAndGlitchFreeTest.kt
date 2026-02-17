package io.github.fenrur.signal

import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.operators.*
import io.github.fenrur.signal.operators.and
import io.github.fenrur.signal.operators.bimap
import io.github.fenrur.signal.operators.div
import io.github.fenrur.signal.operators.filter
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.minus
import io.github.fenrur.signal.operators.not
import io.github.fenrur.signal.operators.or
import io.github.fenrur.signal.operators.pairwise
import io.github.fenrur.signal.operators.plus
import io.github.fenrur.signal.operators.rem
import io.github.fenrur.signal.operators.scan
import io.github.fenrur.signal.operators.times
import io.github.fenrur.signal.operators.withLatestFrom
import io.github.fenrur.signal.operators.xor
import kotlin.test.*

/**
 * Single-threaded glitch-free and consistency tests.
 *
 * Signal graphs are illustrated with ASCII diagrams above each test:
 * - Arrows show data flow direction (top to bottom)
 * - Letters represent signals (a, b, c, etc.)
 * - Operators are shown inline where relevant
 */
class ConcurrencyAndGlitchFreeTest {

    // =========================================================================
    // GLITCH-FREE TESTS - DIAMOND PATTERNS
    // These tests verify that derived signals never see inconsistent states
    // =========================================================================

    @Test
    fun `glitch-free - diamond dependency single update produces single emission`() {
        //     a
        //    / \
        //   b   |
        //    \ /
        //     c
        //
        // b = a.map { it * 2 }
        // c = combine(a, b)
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = combine(a, b) { x, y -> x + y }

        val emissions = mutableListOf<Int>()
        c.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial emission: a=1, b=2, c=1+2=3
        assertEquals(listOf(3), emissions.toList())
        emissions.clear()

        // After update: a=2, b=4, c=2+4=6
        a.value = 2

        assertEquals(1, emissions.size, "Should emit once, got ${emissions.size} emissions: $emissions")
        assertEquals(6, emissions[0])
    }

    @Test
    fun `glitch-free - diamond dependency value is always consistent when read`() {
        //     a
        //    / \
        //   b   |
        //    \ /
        //     c
        //
        // b = a * 2
        // c = a + b
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = combine(a, b) { x, y -> x + y }

        // Verify consistency at each step
        assertEquals(3, c.value)  // a=1, b=2, c=3

        a.value = 5
        assertEquals(15, c.value) // a=5, b=10, c=15

        a.value = 10
        assertEquals(30, c.value) // a=10, b=20, c=30
    }

    @Test
    fun `glitch-free - triple diamond dependency single emission per update`() {
        //       a
        //      /|\
        //     b c |
        //      \|/
        //       d
        //
        // b = a * 2
        // c = a * 3
        // d = a + b + c
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = a.map { it * 3 }
        val d = combine(a, b, c) { x, y, z -> x + y + z }

        val emissions = mutableListOf<Int>()
        d.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=2, c=3, d=1+2+3=6
        assertEquals(listOf(6), emissions.toList())
        emissions.clear()

        // After update: a=2, b=4, c=6, d=2+4+6=12
        a.value = 2

        assertEquals(1, emissions.size, "Should emit once, got ${emissions.size} emissions: $emissions")
        assertEquals(12, emissions[0])
    }

    @Test
    fun `glitch-free - deep diamond chain no intermediate states observed`() {
        //     a
        //    / \
        //   b   c
        //    \ /
        //     d
        //    / \
        //   e   f
        //    \ /
        //     g
        //
        // b = a * 2, c = a * 3
        // d = b + c
        // e = d * 2, f = d * 3
        // g = e + f
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = a.map { it * 3 }
        val d = combine(b, c) { x, y -> x + y }
        val e = d.map { it * 2 }
        val f = d.map { it * 3 }
        val g = combine(e, f) { x, y -> x + y }

        val emissions = mutableListOf<Int>()
        g.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=2, c=3, d=5, e=10, f=15, g=25
        assertEquals(listOf(25), emissions.toList())
        emissions.clear()

        // After update: a=2, b=4, c=6, d=10, e=20, f=30, g=50
        a.value = 2

        assertEquals(1, emissions.size, "Should emit once, got ${emissions.size} emissions: $emissions")
        assertEquals(50, emissions[0])
    }

    @Test
    fun `glitch-free - complex graph with shared dependencies`() {
        //       a
        //      /|\
        //     / | \
        //    b  c  d
        //     \ | /|
        //      \|/ |
        //       e  |
        //        \ |
        //         \|
        //          f
        //
        // b = a + 1, c = a + 2, d = a + 3
        // e = b + c + d
        // f = e + d
        val a = DefaultMutableSignal(1)
        val b = a.map { it + 1 }
        val c = a.map { it + 2 }
        val d = a.map { it + 3 }
        val e = combine(b, c, d) { x, y, z -> x + y + z }
        val f = combine(e, d) { x, y -> x + y }

        val emissions = mutableListOf<Int>()
        f.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=2, c=3, d=4, e=9, f=13
        assertEquals(listOf(13), emissions.toList())
        emissions.clear()

        // After update: a=10, b=11, c=12, d=13, e=36, f=49
        a.value = 10

        assertEquals(1, emissions.size, "Should emit once, got ${emissions.size} emissions: $emissions")
        assertEquals(49, emissions[0])
    }

    // =========================================================================
    // GLITCH-FREE TESTS - BATCH OPERATIONS
    // =========================================================================

    @Test
    fun `glitch-free - batch multiple signal updates produce single emission`() {
        //   a     b
        //    \   /
        //     \ /
        //      c
        //
        // c = a + b
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val c = combine(a, b) { x, y -> x + y }

        val emissions = mutableListOf<Int>()
        c.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: c = 1 + 10 = 11
        assertEquals(listOf(11), emissions.toList())
        emissions.clear()

        // Batch update
        batch {
            a.value = 2
            b.value = 20
        }

        // Should only see final state: 2 + 20 = 22
        assertEquals(1, emissions.size, "Should emit once in batch, got ${emissions.size} emissions: $emissions")
        assertEquals(22, emissions[0])
    }

    @Test
    fun `glitch-free - nested batch operations`() {
        //   a     b
        //    \   /
        //     \ /
        //      c
        //
        // c = a + b
        val a = mutableSignalOf(0)
        val b = mutableSignalOf(0)
        val c = combine(a, b) { x, y -> x + y }

        val emissions = mutableListOf<Int>()
        c.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        batch {
            a.value = 1
            batch {
                b.value = 2
                a.value = 3
            }
            b.value = 4
        }

        // Should only emit final state: 3 + 4 = 7
        assertEquals(listOf(7), emissions.toList())
    }

    @Test
    fun `glitch-free - batch with three sources`() {
        //   a   b   c
        //    \  |  /
        //     \ | /
        //      \|/
        //       d
        //
        // d = a + b + c
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val c = DefaultMutableSignal(100)
        val d = combine(a, b, c) { x, y, z -> x + y + z }

        val emissions = mutableListOf<Int>()
        d.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: d = 1 + 10 + 100 = 111
        assertEquals(listOf(111), emissions.toList())
        emissions.clear()

        // Nested batch update
        batch {
            a.value = 2
            batch {
                b.value = 20
            }
            c.value = 200
        }

        // Should only see final state: 2 + 20 + 200 = 222
        assertEquals(1, emissions.size, "Should emit once with nested batch, got ${emissions.size} emissions: $emissions")
        assertEquals(222, emissions[0])
    }

    @Test
    fun `glitch-free - batch value is consistent during batch`() {
        //   a     b
        //    \   /
        //     \ /
        //      c
        //
        // c = a + b
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val c = combine(a, b) { x, y -> x + y }

        var valueReadDuringBatch: Int? = null

        batch {
            a.value = 2
            valueReadDuringBatch = c.value
            b.value = 20
        }

        // During batch, reading should get updated value for a but old for b
        assertEquals(12, valueReadDuringBatch) // 2 + 10

        // After batch, full consistency
        assertEquals(22, c.value)
    }

    // =========================================================================
    // GLITCH-FREE TESTS - OPERATORS
    // =========================================================================

    @Test
    fun `glitch-free - filter with source change`() {
        //   source
        //     |
        //   doubled (map)
        //     |
        //   filtered (filter > 15)
        //
        val source = mutableSignalOf(10)
        val doubled = source.map { it * 2 }
        val filtered = doubled.filter { it > 15 }

        val emissions = mutableListOf<Int>()
        filtered.subscribe { it.onSuccess { v -> emissions.add(v) } }

        assertEquals(20, filtered.value)
        emissions.clear()

        source.value = 5  // doubled = 10, doesn't pass filter
        assertTrue(emissions.isEmpty())
        assertEquals(20, filtered.value)  // Retains last valid value

        source.value = 15  // doubled = 30, passes filter
        assertEquals(listOf(30), emissions.toList())
    }

    @Test
    fun `glitch-free - filter in diamond does not cause glitch`() {
        //       a
        //      / \
        //     b   filtered
        //      \ /
        //       c
        //
        // b = a * 2
        // filtered = a (if > 0 else 1)
        // c = filtered + b
        val a = DefaultMutableSignal(2)
        val b = a.map { it * 2 }
        val filtered = a.map { if (it > 0) it else 1 }
        val c = combine(filtered, b) { x, y -> x + y }

        val emissions = mutableListOf<Int>()
        c.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=2, b=4, filtered=2, c=6
        assertEquals(listOf(6), emissions.toList())
        emissions.clear()

        // After update: a=5, b=10, filtered=5, c=15
        a.value = 5

        assertEquals(1, emissions.size)
        assertEquals(15, emissions[0])
    }

    @Test
    fun `glitch-free - scan accumulator consistency`() {
        //   source
        //     |
        //    sum (scan + accumulate)
        //
        val source = mutableSignalOf(1)
        val sum = source.scan(0) { acc, v -> acc + v }

        val emissions = mutableListOf<Int>()
        sum.subscribe { it.onSuccess { v -> emissions.add(v) } }

        assertEquals(1, sum.value)  // 0 + 1
        emissions.clear()

        // In a batch, only the final source value is seen
        batch {
            source.value = 2
            source.value = 3
            source.value = 4
        }

        // Scan sees: initial=1, then final batch value=4
        // So: 1 + 4 = 5
        assertEquals(5, sum.value)
    }

    @Test
    fun `glitch-free - withLatestFrom samples correctly`() {
        //   trigger    sampled
        //      |          |
        //      +----+-----+
        //           |
        //        result (withLatestFrom)
        //
        val trigger = mutableSignalOf(0)
        val sampled = mutableSignalOf("A")
        val result = trigger.withLatestFrom(sampled) { t, s -> "$t:$s" }

        val emissions = mutableListOf<String>()
        result.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        // Change sampled but not trigger - no emission
        sampled.value = "B"
        assertTrue(emissions.isEmpty())

        // Change trigger - should sample latest from sampled
        trigger.value = 1
        assertEquals(listOf("1:B"), emissions.toList())

        // Batch update both
        batch {
            sampled.value = "C"
            trigger.value = 2
        }
        assertTrue(emissions.contains("2:C"))
    }

    @Test
    fun `glitch-free - pairwise consistency`() {
        //   source
        //     |
        //   pairs (pairwise)
        //
        val source = mutableSignalOf(1)
        val pairs = source.pairwise()

        val emissions = mutableListOf<Pair<Int, Int>>()
        pairs.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        // In a batch, only the final value is seen
        batch {
            source.value = 2
            source.value = 3
        }

        // After batch, pairwise shows (previous=1, current=3)
        assertEquals(1 to 3, pairs.value)
    }

    // =========================================================================
    // GLITCH-FREE TESTS - CONSISTENCY GUARANTEES
    // =========================================================================

    @Test
    fun `glitch-free - subscriber never observes inconsistent intermediate state`() {
        //     a
        //    / \
        //   b   |
        //    \ /
        //     c
        //
        // b = a * 2
        // c = a + b
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = combine(a, b) { x, y -> x + y }

        // Track all observed states
        val observedStates = mutableListOf<Triple<Int, Int, Int>>()

        c.subscribe { result ->
            result.onSuccess {
                // When c notifies, read a, b, and c to verify consistency
                observedStates.add(Triple(a.value, b.value, c.value))
            }
        }

        // Perform multiple updates
        a.value = 2
        a.value = 5
        a.value = 10

        // Verify all observed states are consistent
        observedStates.forEach { (aVal, bVal, cVal) ->
            assertEquals(aVal * 2, bVal, "b should be a*2, but a=$aVal, b=$bVal")
            assertEquals(aVal + bVal, cVal, "c should be a+b, but a=$aVal, b=$bVal, c=$cVal")
        }
    }

    @Test
    fun `glitch-free - mapped signal never returns stale value`() {
        //   a
        //   |
        //   b (map * 10)
        //
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 10 }

        assertEquals(10, b.value)

        a.value = 2
        assertEquals(20, b.value)

        a.value = 3
        assertEquals(30, b.value)
    }

    @Test
    fun `glitch-free - combined signal with multiple sources stays consistent`() {
        //   a   b   c
        //    \  |  /
        //     \ | /
        //      \|/
        //    combined
        //
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(10)
        val c = DefaultMutableSignal(100)

        val combined = combine(a, b, c) { x, y, z -> x + y + z }

        val emissions = mutableListOf<Int>()
        combined.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial
        assertEquals(listOf(111), emissions.toList())

        // Update each signal individually
        a.value = 2
        b.value = 20
        c.value = 200

        // Each update should produce exactly one consistent emission
        assertEquals(listOf(111, 112, 122, 222), emissions.toList())
    }

    // =========================================================================
    // GLITCH-FREE TESTS - EDGE CASES
    // =========================================================================

    @Test
    fun `glitch-free - same value update does not trigger emission`() {
        //   a
        //   |
        //   b (map * 2)
        //
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }

        val emissions = mutableListOf<Int>()
        b.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial
        assertEquals(listOf(2), emissions.toList())
        emissions.clear()

        // Same value update
        a.value = 1

        // No emission expected
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun `glitch-free - version tracking prevents recomputation when unchanged`() {
        //   a
        //   |
        //   b (map with counter)
        //
        var computeCount = 0
        val a = DefaultMutableSignal(1)
        val b = a.map {
            computeCount++
            it * 2
        }

        // Initial computation
        assertEquals(2, b.value)
        val countAfterFirst = computeCount

        // Reading again without change should not recompute
        assertEquals(2, b.value)
        assertEquals(2, b.value)
        assertEquals(countAfterFirst, computeCount)

        // Changing source should trigger recompute
        a.value = 2
        assertEquals(4, b.value)
        assertTrue(computeCount > countAfterFirst)
    }

    // =========================================================================
    // LAZY SUBSCRIPTION TESTS
    // =========================================================================

    @Test
    fun `lazy - signal doesn't receive push updates until subscribed`() {
        //   source
        //     |
        //   mapped (lazy - no push until subscribed)
        //
        var computeCount = 0
        val source = mutableSignalOf(1)
        val mapped = source.map {
            computeCount++
            it * 2
        }

        // Initial computation happens at creation time
        val initialCount = computeCount
        assertEquals(1, initialCount)

        // Reading value returns cached value
        val v1 = mapped.value
        assertEquals(2, v1)
        assertEquals(1, computeCount)

        // Without subscription, updating source doesn't trigger push
        source.value = 2
        val countBeforePull = computeCount
        val v2 = mapped.value  // Pull triggers recomputation
        assertEquals(4, v2)
        assertTrue(computeCount > countBeforePull)

        // Subscribe - now we get push notifications
        val countBeforeSubscribe = computeCount
        val unsub = mapped.subscribe { }

        // Update source - push notification triggers recomputation
        source.value = 3
        assertTrue(computeCount > countBeforeSubscribe)
        assertEquals(6, mapped.value)

        // Unsubscribe
        unsub()

        // After unsubscribe, updates don't trigger push
        val countAfterUnsub = computeCount
        source.value = 4

        // But reading value will trigger pull
        val v3 = mapped.value
        assertEquals(8, v3)
        assertTrue(computeCount > countAfterUnsub)
    }

    @Test
    fun `lazy - nested signals resubscribe correctly`() {
        //   source
        //     |
        //   level1 (map * 2)
        //     |
        //   level2 (map + 10)
        //     |
        //   level3 (map * 3)
        //
        val source = mutableSignalOf(1)
        val level1 = source.map { it * 2 }
        val level2 = level1.map { it + 10 }
        val level3 = level2.map { it * 3 }

        // Subscribe to level3
        val emissions = mutableListOf<Int>()
        val unsub = level3.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        // Update and verify
        source.value = 2
        assertEquals(listOf(42), emissions.toList())  // ((2*2)+10)*3 = 42

        // Unsubscribe
        unsub()
        emissions.clear()

        // Update - no emission
        source.value = 3

        // Re-subscribe
        val unsub2 = level3.subscribe { it.onSuccess { v -> emissions.add(v) } }

        // Should get current value
        assertEquals(listOf(48), emissions.toList())  // ((3*2)+10)*3 = 48

        unsub2()
    }

    // =========================================================================
    // CONSISTENCY TESTS
    // =========================================================================

    @Test
    fun `consistency - boolean operators remain consistent`() {
        //   a     b
        //   |\ /| |
        //   | X | |
        //   |/ \| |
        //  and or xor  notA
        //
        val a = mutableSignalOf(true)
        val b = mutableSignalOf(false)

        val and = a.and(b)
        val or = a.or(b)
        val xor = a.xor(b)
        val notA = a.not()

        // Subscribe to all
        and.subscribe { }
        or.subscribe { }
        xor.subscribe { }
        notA.subscribe { }

        // Verify initial state
        assertFalse(and.value)
        assertTrue(or.value)
        assertTrue(xor.value)
        assertFalse(notA.value)

        // Update in batch
        batch {
            a.value = false
            b.value = true
        }

        assertFalse(and.value)
        assertTrue(or.value)
        assertTrue(xor.value)
        assertTrue(notA.value)

        // Both true
        batch {
            a.value = true
            b.value = true
        }

        assertTrue(and.value)
        assertTrue(or.value)
        assertFalse(xor.value)
    }

    @Test
    fun `consistency - numeric operators remain consistent`() {
        //   a       b
        //   |\ /\ /|
        //   | X  X |
        //   |/ \/ \|
        //  sum diff prod quot rem
        //
        val a = mutableSignalOf(10)
        val b = mutableSignalOf(3)

        val sum = a + b
        val diff = a - b
        val prod = a * b
        val quot = a / b
        val rem = a % b

        // Subscribe to all
        sum.subscribe { }
        diff.subscribe { }
        prod.subscribe { }
        quot.subscribe { }
        rem.subscribe { }

        // Verify initial state
        assertEquals(13, sum.value)
        assertEquals(7, diff.value)
        assertEquals(30, prod.value)
        assertEquals(3, quot.value)
        assertEquals(1, rem.value)

        // Update in batch
        batch {
            a.value = 20
            b.value = 4
        }

        assertEquals(24, sum.value)
        assertEquals(16, diff.value)
        assertEquals(80, prod.value)
        assertEquals(5, quot.value)
        assertEquals(0, rem.value)
    }

    // =========================================================================
    // SCAN TESTS
    // =========================================================================

    @Test
    fun `scan - identity function preserves values`() {
        val source = mutableSignalOf(5)
        val scanned = source.scan(0) { acc, value -> acc + value }

        assertEquals(5, scanned.value) // 0 + 5

        source.value = 3
        assertEquals(8, scanned.value) // 5 + 3

        source.value = 2
        assertEquals(10, scanned.value) // 8 + 2
    }

    @Test
    fun `scan - initial equals first source value`() {
        val source = mutableSignalOf(10)
        val scanned = source.scan(10) { acc, value -> acc + value }

        // Initial: 10 + 10 = 20
        assertEquals(20, scanned.value)

        // Setting same value doesn't trigger recomputation (value didn't change)
        source.value = 10
        assertEquals(20, scanned.value)

        // Setting different value does trigger recomputation
        source.value = 5
        assertEquals(25, scanned.value)  // 20 + 5
    }

    // =========================================================================
    // NUMERIC OPERATOR EDGE CASES
    // =========================================================================

    @Ignore
    @Test
    fun `numeric operators - division by zero handling`() {
        val numerator = mutableSignalOf(10)
        val denominator = mutableSignalOf(2)
        val result = numerator / denominator

        assertEquals(5, result.value)

        // Division by zero behavior is platform-dependent:
        // JVM/Native: throws ArithmeticException
        // Wasm: throws RuntimeError (trap)
        // JS: returns Infinity (no exception)
        denominator.value = 0
        try {
            result.value
        } catch (_: Throwable) {
            // Expected on JVM/Native/Wasm
        }

        // Recovery: set back to non-zero
        denominator.value = 5
        assertEquals(2, result.value)
    }

    // =========================================================================
    // BIMAP EDGE CASES
    // =========================================================================

    @Test
    fun `bimap - forward transform exception is caught and propagated`() {
        val source = mutableSignalOf(10)
        val bimapped = source.bimap(
            forward = { if (it == 0) throw IllegalArgumentException("Zero not allowed") else it * 2 },
            reverse = { it / 2 }
        )

        val errors = mutableListOf<Throwable>()
        bimapped.subscribe { result ->
            result.onSuccess { }
            result.onFailure { errors.add(it) }
        }

        // Normal value works
        assertEquals(20, bimapped.value)
        assertTrue(errors.isEmpty())

        // Set to zero to trigger exception
        source.value = 0

        // Reading value should throw
        try {
            bimapped.value
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Error should be propagated to listeners
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalArgumentException)
    }

    @Test
    fun `bimap - reverse transform exception is caught and propagated`() {
        val source = mutableSignalOf(10)
        val bimapped = source.bimap(
            forward = { it * 2 },
            reverse = { if (it == 0) throw IllegalArgumentException("Zero not allowed") else it / 2 }
        )

        val errors = mutableListOf<Throwable>()
        bimapped.subscribe { result ->
            result.onSuccess { }
            result.onFailure { errors.add(it) }
        }

        // Setting zero through bimapped should throw
        try {
            bimapped.value = 0
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Error should be propagated
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalArgumentException)
    }
}
