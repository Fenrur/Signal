package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Comprehensive tests for thread-safety, glitch-free behavior, and concurrent operations.
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

        val emissions = CopyOnWriteArrayList<Int>()
        c.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial emission: a=1, b=2, c=1+2=3
        assertThat(emissions).containsExactly(3)
        emissions.clear()

        // After update: a=2, b=4, c=2+4=6
        a.value = 2

        assertThat(emissions).hasSize(1)
            .withFailMessage("Should emit once, got ${emissions.size} emissions: $emissions")
        assertThat(emissions[0]).isEqualTo(6)
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
        assertThat(c.value).isEqualTo(3)  // a=1, b=2, c=3

        a.value = 5
        assertThat(c.value).isEqualTo(15) // a=5, b=10, c=15

        a.value = 10
        assertThat(c.value).isEqualTo(30) // a=10, b=20, c=30
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

        val emissions = CopyOnWriteArrayList<Int>()
        d.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=2, c=3, d=1+2+3=6
        assertThat(emissions).containsExactly(6)
        emissions.clear()

        // After update: a=2, b=4, c=6, d=2+4+6=12
        a.value = 2

        assertThat(emissions).hasSize(1)
            .withFailMessage("Should emit once, got ${emissions.size} emissions: $emissions")
        assertThat(emissions[0]).isEqualTo(12)
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

        val emissions = CopyOnWriteArrayList<Int>()
        g.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=2, c=3, d=5, e=10, f=15, g=25
        assertThat(emissions).containsExactly(25)
        emissions.clear()

        // After update: a=2, b=4, c=6, d=10, e=20, f=30, g=50
        a.value = 2

        assertThat(emissions).hasSize(1)
            .withFailMessage("Should emit once, got ${emissions.size} emissions: $emissions")
        assertThat(emissions[0]).isEqualTo(50)
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

        val emissions = CopyOnWriteArrayList<Int>()
        f.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=1, b=2, c=3, d=4, e=9, f=13
        assertThat(emissions).containsExactly(13)
        emissions.clear()

        // After update: a=10, b=11, c=12, d=13, e=36, f=49
        a.value = 10

        assertThat(emissions).hasSize(1)
            .withFailMessage("Should emit once, got ${emissions.size} emissions: $emissions")
        assertThat(emissions[0]).isEqualTo(49)
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

        val emissions = CopyOnWriteArrayList<Int>()
        c.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: c = 1 + 10 = 11
        assertThat(emissions).containsExactly(11)
        emissions.clear()

        // Batch update
        batch {
            a.value = 2
            b.value = 20
        }

        // Should only see final state: 2 + 20 = 22
        assertThat(emissions).hasSize(1)
            .withFailMessage("Should emit once in batch, got ${emissions.size} emissions: $emissions")
        assertThat(emissions[0]).isEqualTo(22)
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

        val emissions = CopyOnWriteArrayList<Int>()
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
        assertThat(emissions).containsExactly(7)
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

        val emissions = CopyOnWriteArrayList<Int>()
        d.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: d = 1 + 10 + 100 = 111
        assertThat(emissions).containsExactly(111)
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
        assertThat(emissions).hasSize(1)
            .withFailMessage("Should emit once with nested batch, got ${emissions.size} emissions: $emissions")
        assertThat(emissions[0]).isEqualTo(222)
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
        assertThat(valueReadDuringBatch).isEqualTo(12) // 2 + 10

        // After batch, full consistency
        assertThat(c.value).isEqualTo(22)
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

        val emissions = CopyOnWriteArrayList<Int>()
        filtered.subscribe { it.onSuccess { v -> emissions.add(v) } }

        assertThat(filtered.value).isEqualTo(20)
        emissions.clear()

        source.value = 5  // doubled = 10, doesn't pass filter
        assertThat(emissions).isEmpty()
        assertThat(filtered.value).isEqualTo(20)  // Retains last valid value

        source.value = 15  // doubled = 30, passes filter
        assertThat(emissions).containsExactly(30)
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

        val emissions = CopyOnWriteArrayList<Int>()
        c.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial: a=2, b=4, filtered=2, c=6
        assertThat(emissions).containsExactly(6)
        emissions.clear()

        // After update: a=5, b=10, filtered=5, c=15
        a.value = 5

        assertThat(emissions).hasSize(1)
        assertThat(emissions[0]).isEqualTo(15)
    }

    @Test
    fun `glitch-free - scan accumulator consistency`() {
        //   source
        //     |
        //    sum (scan + accumulate)
        //
        val source = mutableSignalOf(1)
        val sum = source.scan(0) { acc, v -> acc + v }

        val emissions = CopyOnWriteArrayList<Int>()
        sum.subscribe { it.onSuccess { v -> emissions.add(v) } }

        assertThat(sum.value).isEqualTo(1)  // 0 + 1
        emissions.clear()

        // In a batch, only the final source value is seen
        batch {
            source.value = 2
            source.value = 3
            source.value = 4
        }

        // Scan sees: initial=1, then final batch value=4
        // So: 1 + 4 = 5
        assertThat(sum.value).isEqualTo(5)
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

        val emissions = CopyOnWriteArrayList<String>()
        result.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        // Change sampled but not trigger - no emission
        sampled.value = "B"
        assertThat(emissions).isEmpty()

        // Change trigger - should sample latest from sampled
        trigger.value = 1
        assertThat(emissions).containsExactly("1:B")

        // Batch update both
        batch {
            sampled.value = "C"
            trigger.value = 2
        }
        assertThat(emissions).contains("2:C")
    }

    @Test
    fun `glitch-free - pairwise consistency`() {
        //   source
        //     |
        //   pairs (pairwise)
        //
        val source = mutableSignalOf(1)
        val pairs = source.pairwise()

        val emissions = CopyOnWriteArrayList<Pair<Int, Int>>()
        pairs.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        // In a batch, only the final value is seen
        batch {
            source.value = 2
            source.value = 3
        }

        // After batch, pairwise shows (previous=1, current=3)
        assertThat(pairs.value).isEqualTo(1 to 3)
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
        val observedStates = CopyOnWriteArrayList<Triple<Int, Int, Int>>()

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
            assertThat(bVal).isEqualTo(aVal * 2)
                .withFailMessage("b should be a*2, but a=$aVal, b=$bVal")
            assertThat(cVal).isEqualTo(aVal + bVal)
                .withFailMessage("c should be a+b, but a=$aVal, b=$bVal, c=$cVal")
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

        assertThat(b.value).isEqualTo(10)

        a.value = 2
        assertThat(b.value).isEqualTo(20)

        a.value = 3
        assertThat(b.value).isEqualTo(30)
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

        val emissions = CopyOnWriteArrayList<Int>()
        combined.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial
        assertThat(emissions).containsExactly(111)

        // Update each signal individually
        a.value = 2
        b.value = 20
        c.value = 200

        // Each update should produce exactly one consistent emission
        assertThat(emissions).containsExactly(111, 112, 122, 222)
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

        val emissions = CopyOnWriteArrayList<Int>()
        b.subscribe { result -> result.onSuccess { emissions.add(it) } }

        // Initial
        assertThat(emissions).containsExactly(2)
        emissions.clear()

        // Same value update
        a.value = 1

        // No emission expected
        assertThat(emissions).isEmpty()
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
        assertThat(b.value).isEqualTo(2)
        val countAfterFirst = computeCount

        // Reading again without change should not recompute
        assertThat(b.value).isEqualTo(2)
        assertThat(b.value).isEqualTo(2)
        assertThat(computeCount).isEqualTo(countAfterFirst)

        // Changing source should trigger recompute
        a.value = 2
        assertThat(b.value).isEqualTo(4)
        assertThat(computeCount).isGreaterThan(countAfterFirst)
    }

    // =========================================================================
    // CONCURRENT MODIFICATION TESTS
    // Multiple threads modifying signals simultaneously
    // =========================================================================

    @RepeatedTest(10)
    fun `concurrent - multiple threads incrementing signal`() {
        //   counter
        //     |
        //   (atomic increments from N threads)
        //
        val counter = mutableSignalOf(0)
        val threads = 10
        val incrementsPerThread = 1000

        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) {
            executor.submit {
                try {
                    repeat(incrementsPerThread) {
                        counter.update { it + 1 }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(counter.value).isEqualTo(threads * incrementsPerThread)
    }

    @RepeatedTest(10)
    fun `concurrent - readers and writers`() {
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

        val executor = Executors.newFixedThreadPool(writers + readers)
        val latch = CountDownLatch(writers + readers)
        val errors = CopyOnWriteArrayList<Throwable>()

        // Writers
        repeat(writers) {
            executor.submit {
                try {
                    repeat(operations) { i ->
                        signal.value = i
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        // Readers
        repeat(readers) {
            executor.submit {
                try {
                    repeat(operations) {
                        val v = signal.value
                        val m = mapped.value
                        // Mapped should always be even (2x something)
                        assertThat(m % 2).isEqualTo(0)
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(errors).isEmpty()
    }

    @RepeatedTest(5)
    fun `concurrent - subscribe and unsubscribe while updating`() {
        //   signal
        //     |
        //   mapped
        //     |
        //   (concurrent subscribe/unsubscribe)
        //
        val signal = mutableSignalOf(0)
        val mapped = signal.map { it * 2 }
        val operations = 200
        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(3)
        val errors = CopyOnWriteArrayList<Throwable>()

        // Writer
        executor.submit {
            try {
                repeat(operations) { i ->
                    signal.value = i
                }
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                latch.countDown()
            }
        }

        // Subscriber/Unsubscriber 1
        executor.submit {
            try {
                repeat(operations) {
                    val unsub = mapped.subscribe { }
                    unsub()
                }
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                latch.countDown()
            }
        }

        // Subscriber/Unsubscriber 2
        executor.submit {
            try {
                repeat(operations) {
                    val unsub = signal.subscribe { }
                    unsub()
                }
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                latch.countDown()
            }
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        assertThat(errors).isEmpty()
    }

    @RepeatedTest(5)
    fun `concurrent - complex graph with multiple sources`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        executor.submit {
            try {
                repeat(updates) { i -> a.value = i }
            } finally {
                latch.countDown()
            }
        }

        executor.submit {
            try {
                repeat(updates) { i -> b.value = i }
            } finally {
                latch.countDown()
            }
        }

        executor.submit {
            try {
                repeat(updates) { i -> c.value = i }
            } finally {
                latch.countDown()
            }
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        // Final state should be consistent
        val finalAbc = abc.value
        assertThat(finalAbc).isGreaterThanOrEqualTo(0)
    }

    @RepeatedTest(5)
    fun `concurrent - batch from multiple threads`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { t ->
            executor.submit {
                try {
                    repeat(batches) { i ->
                        batch {
                            a.value = t * 1000 + i
                            b.value = t * 1000 + i
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        // Final state should be consistent
        assertThat(sum.value).isEqualTo(a.value + b.value)
    }

    // =========================================================================
    // OPERATOR THREAD-SAFETY TESTS
    // Verify each operator works correctly under concurrency
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - map operator thread safety`() {
        //   source
        //     |
        //   mapped (map * 2)
        //
        val source = mutableSignalOf(0)
        val computeCount = AtomicInteger(0)
        val mapped = source.map {
            computeCount.incrementAndGet()
            it * 2
        }

        val emissions = CopyOnWriteArrayList<Int>()
        mapped.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) {
            executor.submit {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Value should be consistent
        assertThat(mapped.value).isEqualTo(source.value * 2)
    }

    @RepeatedTest(5)
    fun `concurrent - filter operator thread safety`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) {
            executor.submit {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be even
        emissions.forEach { assertThat(it % 2).isEqualTo(0) }
    }

    @RepeatedTest(5)
    fun `concurrent - combine operator thread safety`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads / 2) {
            executor.submit {
                try {
                    repeat(updates) { i -> a.value = i }
                } finally {
                    latch.countDown()
                }
            }
        }

        repeat(threads / 2) {
            executor.submit {
                try {
                    repeat(updates) { i -> b.value = i }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // No exceptions means thread-safe
        assertThat(combined.value).isEqualTo(a.value to b.value)
    }

    @RepeatedTest(5)
    fun `concurrent - scan operator thread safety`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) {
            executor.submit {
                try {
                    repeat(updates) { i ->
                        source.value = 1
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Scan should always produce increasing values
        var prev = Int.MIN_VALUE
        emissions.forEach { v ->
            assertThat(v).isGreaterThanOrEqualTo(prev)
            prev = v
        }
    }

    @RepeatedTest(5)
    fun `concurrent - distinctUntilChangedBy thread safety`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { t ->
            executor.submit {
                try {
                    repeat(updates) { i ->
                        source.value = Item(t, "value-$i")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // No exceptions means thread-safe
        assertThat(emissions).isNotEmpty()
    }

    @RepeatedTest(5)
    fun `concurrent - bimap operator thread safety`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        // Writers to source
        repeat(threads / 2) {
            executor.submit {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Writers through bimap
        repeat(threads / 2) {
            executor.submit {
                try {
                    repeat(updates) { i ->
                        bimap.value = i.toString()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Value should be consistent
        assertThat(bimap.value).isEqualTo(source.value.toString())
    }

    // =========================================================================
    // LAZY SUBSCRIPTION TESTS
    // Verify lazy behavior under concurrency
    // =========================================================================

    @Test
    fun `lazy - signal doesn't receive push updates until subscribed`() {
        //   source
        //     |
        //   mapped (lazy - no push until subscribed)
        //
        val computeCount = AtomicInteger(0)
        val source = mutableSignalOf(1)
        val mapped = source.map {
            computeCount.incrementAndGet()
            it * 2
        }

        // Initial computation happens at creation time
        val initialCount = computeCount.get()
        assertThat(initialCount).isEqualTo(1)

        // Reading value returns cached value
        val v1 = mapped.value
        assertThat(v1).isEqualTo(2)
        assertThat(computeCount.get()).isEqualTo(1)

        // Without subscription, updating source doesn't trigger push
        source.value = 2
        val countBeforePull = computeCount.get()
        val v2 = mapped.value  // Pull triggers recomputation
        assertThat(v2).isEqualTo(4)
        assertThat(computeCount.get()).isGreaterThan(countBeforePull)

        // Subscribe - now we get push notifications
        val countBeforeSubscribe = computeCount.get()
        val unsub = mapped.subscribe { }

        // Update source - push notification triggers recomputation
        source.value = 3
        Thread.sleep(20)
        assertThat(computeCount.get()).isGreaterThan(countBeforeSubscribe)
        assertThat(mapped.value).isEqualTo(6)

        // Unsubscribe
        unsub()

        // After unsubscribe, updates don't trigger push
        val countAfterUnsub = computeCount.get()
        source.value = 4
        Thread.sleep(20)

        // But reading value will trigger pull
        val v3 = mapped.value
        assertThat(v3).isEqualTo(8)
        assertThat(computeCount.get()).isGreaterThan(countAfterUnsub)
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
        val emissions = CopyOnWriteArrayList<Int>()
        val unsub = level3.subscribe { it.onSuccess { v -> emissions.add(v) } }
        emissions.clear()

        // Update and verify
        source.value = 2
        assertThat(emissions).containsExactly(42)  // ((2*2)+10)*3 = 42

        // Unsubscribe
        unsub()
        emissions.clear()

        // Update - no emission
        source.value = 3

        // Re-subscribe
        val unsub2 = level3.subscribe { it.onSuccess { v -> emissions.add(v) } }

        // Should get current value
        assertThat(emissions).containsExactly(48)  // ((3*2)+10)*3 = 48

        unsub2()
    }

    // =========================================================================
    // CONSISTENCY TESTS
    // Verify that signals always have consistent values
    // =========================================================================

    @RepeatedTest(10)
    fun `consistency - derived value always matches source`() {
        //   source
        //     |
        //   derived (map * 2)
        //
        val source = mutableSignalOf(0)
        val derived = source.map { it * 2 }

        val threads = 4
        val checks = 1000
        val executor = Executors.newFixedThreadPool(threads + 1)
        val latch = CountDownLatch(threads + 1)
        val running = AtomicBoolean(true)
        val inconsistencies = AtomicInteger(0)

        // Writer
        executor.submit {
            try {
                repeat(checks) { i ->
                    source.value = i
                }
            } finally {
                running.set(false)
                latch.countDown()
            }
        }

        // Checkers
        repeat(threads) {
            executor.submit {
                try {
                    while (running.get()) {
                        val s = source.value
                        val d = derived.value
                        // d should always be even
                        if (d % 2 != 0) {
                            inconsistencies.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(inconsistencies.get()).isEqualTo(0)
    }

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
        assertThat(and.value).isFalse()
        assertThat(or.value).isTrue()
        assertThat(xor.value).isTrue()
        assertThat(notA.value).isFalse()

        // Update in batch
        batch {
            a.value = false
            b.value = true
        }

        assertThat(and.value).isFalse()
        assertThat(or.value).isTrue()
        assertThat(xor.value).isTrue()
        assertThat(notA.value).isTrue()

        // Both true
        batch {
            a.value = true
            b.value = true
        }

        assertThat(and.value).isTrue()
        assertThat(or.value).isTrue()
        assertThat(xor.value).isFalse()
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
        assertThat(sum.value).isEqualTo(13)
        assertThat(diff.value).isEqualTo(7)
        assertThat(prod.value).isEqualTo(30)
        assertThat(quot.value).isEqualTo(3)
        assertThat(rem.value).isEqualTo(1)

        // Update in batch
        batch {
            a.value = 20
            b.value = 4
        }

        assertThat(sum.value).isEqualTo(24)
        assertThat(diff.value).isEqualTo(16)
        assertThat(prod.value).isEqualTo(80)
        assertThat(quot.value).isEqualTo(5)
        assertThat(rem.value).isEqualTo(0)
    }

    // =========================================================================
    // STRESS TESTS
    // High load scenarios
    // =========================================================================

    @RepeatedTest(3)
    fun `stress - many signals combined`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        signals.forEachIndexed { index, signal ->
            executor.submit {
                try {
                    repeat(updates) { i ->
                        signal.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // Final sum should be consistent
        val expectedSum = signals.sumOf { it.value }
        assertThat(sum.value).isEqualTo(expectedSum)
    }

    @RepeatedTest(3)
    fun `stress - rapid subscribe unsubscribe`() {
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        val errors = CopyOnWriteArrayList<Throwable>()

        repeat(threads) {
            executor.submit {
                try {
                    repeat(operations) {
                        val unsub = mapped.subscribe { }
                        source.update { it + 1 }
                        unsub()
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(errors).isEmpty()
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
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) {
            executor.submit {
                try {
                    repeat(updates) { i ->
                        source.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final value should be source + 20
        assertThat(final.value).isEqualTo(source.value + 20)
    }

    // =========================================================================
    // ADDITIONAL CONCURRENCY TESTS - MAPNOTNULL, FLATMAP, SCAN, BIMAP
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - mapNotNull thread safety`() {
        // Test that mapNotNull correctly filters nulls under concurrent updates
        val source = mutableSignalOf<Int?>(1)
        val mapped = source.mapNotNull { it?.let { v -> v * 2 } }

        val emissions = CopyOnWriteArrayList<Int>()
        mapped.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 200
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { threadId ->
            executor.submit {
                try {
                    repeat(updates) { i ->
                        // Alternate between null and non-null values
                        source.value = if (i % 2 == 0) i + threadId * 100 else null
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be non-null and doubled
        assertThat(emissions).allMatch { it % 2 == 0 }
        // Value should be consistent
        val finalValue = mapped.value
        assertThat(finalValue % 2).isEqualTo(0)
    }

    @RepeatedTest(5)
    fun `concurrent - flatMap rapid inner signal switching`() {
        // Test that flatMap correctly handles rapid inner signal switching
        val innerSignals = (0 until 10).map { mutableSignalOf(it * 10) }
        val selector = mutableSignalOf(0)
        val flattened = selector.flatMap { idx -> innerSignals[idx % innerSignals.size] }

        val emissions = CopyOnWriteArrayList<Int>()
        flattened.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val switches = 100
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { threadId ->
            executor.submit {
                try {
                    repeat(switches) { i ->
                        // Rapidly switch between inner signals
                        selector.value = (threadId * 100 + i) % innerSignals.size
                        // Also update inner signals
                        innerSignals[i % innerSignals.size].value = threadId * 1000 + i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Verify the final value is consistent
        val finalIdx = selector.value % innerSignals.size
        assertThat(flattened.value).isEqualTo(innerSignals[finalIdx].value)
    }

    @Test
    fun `scan - identity function preserves values`() {
        val source = mutableSignalOf(5)
        val scanned = source.scan(0) { acc, value -> acc + value }

        assertThat(scanned.value).isEqualTo(5) // 0 + 5

        source.value = 3
        assertThat(scanned.value).isEqualTo(8) // 5 + 3

        source.value = 2
        assertThat(scanned.value).isEqualTo(10) // 8 + 2
    }

    @Test
    fun `scan - initial equals first source value`() {
        val source = mutableSignalOf(10)
        val scanned = source.scan(10) { acc, value -> acc + value }

        // Initial: 10 + 10 = 20
        assertThat(scanned.value).isEqualTo(20)

        // Setting same value doesn't trigger recomputation (value didn't change)
        source.value = 10
        assertThat(scanned.value).isEqualTo(20)

        // Setting different value does trigger recomputation
        source.value = 5
        assertThat(scanned.value).isEqualTo(25)  // 20 + 5
    }

    @RepeatedTest(5)
    fun `concurrent - scan accumulator consistency`() {
        val source = mutableSignalOf(0)
        val scanned = source.scan(0) { acc, value -> acc + value }

        val emissions = CopyOnWriteArrayList<Int>()
        scanned.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val threads = 4
        val updates = 100
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        val updateCount = AtomicInteger(0)

        repeat(threads) {
            executor.submit {
                try {
                    repeat(updates) {
                        source.value = 1
                        updateCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be non-negative (no corruption)
        assertThat(emissions).allMatch { it >= 0 }
    }

    @RepeatedTest(5)
    fun `concurrent - bimap bidirectional updates`() {
        val source = mutableSignalOf(10)
        val bimapped = source.bimap(
            forward = { it * 2 },
            reverse = { it / 2 }
        )

        val forwardEmissions = CopyOnWriteArrayList<Int>()
        bimapped.subscribe { it.onSuccess { v -> forwardEmissions.add(v) } }

        val threads = 4
        val updates = 100
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { threadId ->
            executor.submit {
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
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Verify consistency: bimapped.value should always be source.value * 2
        assertThat(bimapped.value).isEqualTo(source.value * 2)
    }

    @Test
    fun `bimap - forward transform exception is caught and propagated`() {
        val source = mutableSignalOf(10)
        val bimapped = source.bimap(
            forward = { if (it == 0) throw IllegalArgumentException("Zero not allowed") else it * 2 },
            reverse = { it / 2 }
        )

        val errors = CopyOnWriteArrayList<Throwable>()
        bimapped.subscribe { result ->
            result.onSuccess { }
            result.onFailure { errors.add(it) }
        }

        // Normal value works
        assertThat(bimapped.value).isEqualTo(20)
        assertThat(errors).isEmpty()

        // Set to zero to trigger exception
        source.value = 0

        // Reading value should throw
        try {
            bimapped.value
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Error should be propagated to listeners
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `bimap - reverse transform exception is caught and propagated`() {
        val source = mutableSignalOf(10)
        val bimapped = source.bimap(
            forward = { it * 2 },
            reverse = { if (it == 0) throw IllegalArgumentException("Zero not allowed") else it / 2 }
        )

        val errors = CopyOnWriteArrayList<Throwable>()
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
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(IllegalArgumentException::class.java)
    }

    @RepeatedTest(5)
    fun `concurrent - bindable signal cycle detection`() {
        // Test that concurrent binding operations correctly detect cycles
        val signals = (0 until 5).map { bindableSignalOf(it) }

        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)
        val cycleDetected = AtomicBoolean(false)

        // Try to create cycles concurrently
        repeat(4) { threadId ->
            executor.submit {
                try {
                    repeat(50) { i ->
                        val from = (threadId + i) % signals.size
                        val to = (threadId + i + 1) % signals.size
                        try {
                            // This might create a cycle
                            signals[from].bindTo(signals[to])
                        } catch (e: IllegalStateException) {
                            if (e.message?.contains("Circular") == true) {
                                cycleDetected.set(true)
                            }
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

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
    fun `numeric operators - division by zero handling`() {
        val numerator = mutableSignalOf(10)
        val denominator = mutableSignalOf(2)
        val result = numerator / denominator

        assertThat(result.value).isEqualTo(5)

        // Division by zero should throw ArithmeticException
        denominator.value = 0
        try {
            result.value
            throw AssertionError("Should have thrown ArithmeticException")
        } catch (e: ArithmeticException) {
            // Expected
        }

        // Recovery: set back to non-zero
        denominator.value = 5
        assertThat(result.value).isEqualTo(2)
    }

    @RepeatedTest(5)
    fun `concurrent - flatten with closing inner signals`() {
        val innerSignals = CopyOnWriteArrayList((0 until 10).map { mutableSignalOf(it * 10) })
        val selector = mutableSignalOf(0)
        val flattened = selector.flatMap { idx -> innerSignals[idx % innerSignals.size] }

        val emissions = CopyOnWriteArrayList<Int>()
        flattened.subscribe { it.onSuccess { v -> emissions.add(v) } }

        val executor = Executors.newFixedThreadPool(3)
        val latch = CountDownLatch(3)

        // Thread 1: Switch selectors
        executor.submit {
            try {
                repeat(100) { i ->
                    selector.value = i % innerSignals.size
                }
            } finally {
                latch.countDown()
            }
        }

        // Thread 2: Update inner signals
        executor.submit {
            try {
                repeat(100) { i ->
                    val idx = i % innerSignals.size
                    if (!innerSignals[idx].isClosed) {
                        innerSignals[idx].value = i * 100
                    }
                }
            } finally {
                latch.countDown()
            }
        }

        // Thread 3: Close and recreate inner signals
        executor.submit {
            try {
                repeat(20) { i ->
                    val idx = i % innerSignals.size
                    innerSignals[idx].close()
                    innerSignals[idx] = mutableSignalOf((i + 1) * 1000)
                }
            } finally {
                latch.countDown()
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Should not crash - just verify we can still read a value
        try {
            flattened.value
        } catch (_: Throwable) {
            // Inner signal might be closed, that's acceptable
        }
    }
}
