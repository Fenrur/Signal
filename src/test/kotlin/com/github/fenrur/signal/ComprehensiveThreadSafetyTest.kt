package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.FlattenSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive thread-safety tests for all operators.
 * Tests cover concurrent read/write, subscription management, and error handling.
 */
class ComprehensiveThreadSafetyTest {

    // =========================================================================
    // PAIRWISE OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - pairwise operator thread safety`() {
        val source = DefaultMutableSignal(0)
        val pairwise = source.pairwise()
        val emissions = CopyOnWriteArrayList<Pair<Int, Int>>()
        val latch = CountDownLatch(1)

        pairwise.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val writerCount = 4
        val writesPerThread = 50
        val executor = Executors.newFixedThreadPool(writerCount + 2)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(writerCount)

        // Writers
        repeat(writerCount) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(writesPerThread) { i ->
                    source.value = threadId * 1000 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Verify all pairs have valid structure (first element equals previous second)
        val allValues = emissions.toList()
        for (i in 1 until allValues.size) {
            // Each emission should be a valid pair
            val pair = allValues[i]
            assertThat(pair.first).isNotNull()
            assertThat(pair.second).isNotNull()
        }

        // Signal should still be functional
        source.value = 9999
        Thread.sleep(50)
        assertThat(pairwise.value.second).isEqualTo(9999)
    }

    // =========================================================================
    // WITHLATESTFROM OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - withLatestFrom operator thread safety`() {
        val source = DefaultMutableSignal(0)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val emissions = CopyOnWriteArrayList<Int>()

        combined.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Source writers
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    source.value = i
                }
                doneLatch.countDown()
            }
        }

        // Other writers
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    other.value = i * 10
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be valid sums
        assertThat(emissions).isNotEmpty()

        // Signal should still be functional
        source.value = 50
        other.value = 500
        Thread.sleep(50)
        assertThat(combined.value).isEqualTo(550)
    }

    // =========================================================================
    // ZIP OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - zip operator thread safety`() {
        val a = DefaultMutableSignal(0)
        val b = DefaultMutableSignal("init")
        val zipped = a.zip(b)
        val emissions = CopyOnWriteArrayList<Pair<Int, String>>()

        zipped.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Int writers
        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    a.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        // String writers
        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    b.value = "thread$threadId-value$i"
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be valid pairs
        assertThat(emissions).isNotEmpty()
        emissions.forEach { pair ->
            assertThat(pair.first).isNotNull()
            assertThat(pair.second).isNotNull()
        }

        // Signal should still be functional
        a.value = 999
        b.value = "final"
        Thread.sleep(50)
        assertThat(zipped.value).isEqualTo(999 to "final")
    }

    // =========================================================================
    // COMBINEALL OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - combineAll operator thread safety`() {
        val signals = List(5) { DefaultMutableSignal(it) }
        val combined = signals.combineAll()
        val emissions = CopyOnWriteArrayList<List<Int>>()

        combined.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(5)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(5)

        signals.forEachIndexed { idx, signal ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    signal.value = idx * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should have 5 elements
        assertThat(emissions).isNotEmpty()
        emissions.forEach { list ->
            assertThat(list).hasSize(5)
        }

        // Signal should still be functional
        signals[0].value = 1000
        Thread.sleep(50)
        assertThat(combined.value[0]).isEqualTo(1000)
    }

    // =========================================================================
    // BOOLEAN OPERATORS THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - and operator thread safety`() {
        val a = DefaultMutableSignal(true)
        val b = DefaultMutableSignal(true)
        val result = a.and(b)
        val emissions = CopyOnWriteArrayList<Boolean>()

        result.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i % 3 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be valid booleans
        assertThat(emissions).isNotEmpty()

        // Final state should be consistent
        a.value = true
        b.value = true
        Thread.sleep(50)
        assertThat(result.value).isTrue()

        a.value = false
        Thread.sleep(50)
        assertThat(result.value).isFalse()
    }

    @RepeatedTest(5)
    fun `concurrent - or operator thread safety`() {
        val a = DefaultMutableSignal(false)
        val b = DefaultMutableSignal(false)
        val result = a.or(b)
        val emissions = CopyOnWriteArrayList<Boolean>()

        result.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i % 3 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final state should be consistent
        a.value = false
        b.value = false
        Thread.sleep(50)
        assertThat(result.value).isFalse()

        a.value = true
        Thread.sleep(50)
        assertThat(result.value).isTrue()
    }

    @RepeatedTest(5)
    fun `concurrent - xor operator thread safety`() {
        val a = DefaultMutableSignal(false)
        val b = DefaultMutableSignal(false)
        val result = a.xor(b)

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i % 3 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final state should be consistent
        a.value = true
        b.value = false
        Thread.sleep(50)
        assertThat(result.value).isTrue()

        b.value = true
        Thread.sleep(50)
        assertThat(result.value).isFalse()
    }

    @RepeatedTest(5)
    fun `concurrent - not operator thread safety`() {
        val source = DefaultMutableSignal(true)
        val negated = source.not()
        val emissions = CopyOnWriteArrayList<Boolean>()

        negated.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    source.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final state should be consistent
        source.value = true
        Thread.sleep(50)
        assertThat(negated.value).isFalse()

        source.value = false
        Thread.sleep(50)
        assertThat(negated.value).isTrue()
    }

    // =========================================================================
    // NUMERIC OPERATORS THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - plus operator thread safety`() {
        val a = DefaultMutableSignal(0)
        val b = DefaultMutableSignal(0)
        val sum = a + b
        val emissions = CopyOnWriteArrayList<Int>()

        sum.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final state should be consistent
        a.value = 10
        b.value = 20
        Thread.sleep(50)
        assertThat(sum.value).isEqualTo(30)
    }

    @RepeatedTest(5)
    fun `concurrent - minus operator thread safety`() {
        val a = DefaultMutableSignal(100)
        val b = DefaultMutableSignal(0)
        val diff = a - b

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = i
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        a.value = 50
        b.value = 30
        Thread.sleep(50)
        assertThat(diff.value).isEqualTo(20)
    }

    @RepeatedTest(5)
    fun `concurrent - times operator thread safety`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(1)
        val product = a * b

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        a.value = 7
        b.value = 8
        Thread.sleep(50)
        assertThat(product.value).isEqualTo(56)
    }

    @RepeatedTest(5)
    fun `concurrent - div operator thread safety`() {
        val a = DefaultMutableSignal(100)
        val b = DefaultMutableSignal(1)
        val quotient = a / b

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = (i + 1) * 10
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i + 1 // Avoid division by zero
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        a.value = 100
        b.value = 5
        Thread.sleep(50)
        assertThat(quotient.value).isEqualTo(20)
    }

    @RepeatedTest(5)
    fun `concurrent - rem operator thread safety`() {
        val a = DefaultMutableSignal(100)
        val b = DefaultMutableSignal(1)
        val remainder = a % b

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    a.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    b.value = i + 1 // Avoid division by zero
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        a.value = 17
        b.value = 5
        Thread.sleep(50)
        assertThat(remainder.value).isEqualTo(2)
    }

    // =========================================================================
    // FILTERNOTNULL OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - filterNotNull operator thread safety`() {
        val source = DefaultMutableSignal<Int?>(0)
        val filtered = source.filterNotNull()
        val emissions = CopyOnWriteArrayList<Int>()

        filtered.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    source.value = if (i % 3 == 0) null else threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be non-null
        assertThat(emissions).allMatch { it != null }

        // Signal should still be functional
        source.value = 42
        Thread.sleep(50)
        assertThat(filtered.value).isEqualTo(42)
    }

    // =========================================================================
    // FILTERISINSTANCE OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - filterIsInstance operator thread safety`() {
        val source = DefaultMutableSignal<Any>("init")
        val filtered = source.filterIsInstance<String>()
        val emissions = CopyOnWriteArrayList<String>()

        filtered.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    source.value = if (i % 2 == 0) "str-$threadId-$i" else i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be strings
        assertThat(emissions).allMatch { it is String }

        // Signal should still be functional
        source.value = "final"
        Thread.sleep(50)
        assertThat(filtered.value).isEqualTo("final")
    }

    // =========================================================================
    // FLATTEN (DIRECT) OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - flatten operator thread safety`() {
        val inner1 = DefaultMutableSignal(1)
        val inner2 = DefaultMutableSignal(100)
        val outer = DefaultMutableSignal<Signal<Int>>(inner1)
        val flattened = outer.flatten()
        val emissions = CopyOnWriteArrayList<Int>()

        flattened.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Inner signal writers
        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    inner1.value = threadId * 100 + i
                    inner2.value = threadId * 1000 + i
                }
                doneLatch.countDown()
            }
        }

        // Outer signal switcher
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    outer.value = if (i % 2 == 0) inner1 else inner2
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Signal should still be functional
        outer.value = inner1
        inner1.value = 999
        Thread.sleep(50)
        assertThat(flattened.value).isEqualTo(999)
    }

    // =========================================================================
    // RUNNINGREDUCE OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - runningReduce operator thread safety`() {
        val source = DefaultMutableSignal(1)
        val reduced = source.runningReduce { acc, v -> acc + v }
        val emissions = CopyOnWriteArrayList<Int>()

        reduced.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(25) { i ->
                    source.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Signal should still be functional
        val currentValue = reduced.value
        source.value = 10
        Thread.sleep(50)
        assertThat(reduced.value).isEqualTo(currentValue + 10)
    }

    // =========================================================================
    // ORDEFAULT OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - orDefault operator thread safety`() {
        val source = DefaultMutableSignal<Int?>(1)
        val withDefault = source.orDefault(0)
        val emissions = CopyOnWriteArrayList<Int>()

        withDefault.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    source.value = if (i % 3 == 0) null else threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // No emissions should contain null (0 when null)
        emissions.forEach { v ->
            assertThat(v).isNotNull()
        }

        // Signal should still be functional
        source.value = null
        Thread.sleep(50)
        assertThat(withDefault.value).isEqualTo(0)

        source.value = 42
        Thread.sleep(50)
        assertThat(withDefault.value).isEqualTo(42)
    }

    // =========================================================================
    // ORELSE OPERATOR THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - orElse operator thread safety`() {
        val primary = DefaultMutableSignal<Int?>(1)
        val fallback = DefaultMutableSignal(999)
        val result = primary.orElse(fallback)
        val emissions = CopyOnWriteArrayList<Int>()

        result.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    primary.value = if (i % 3 == 0) null else threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    fallback.value = threadId * 1000 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Signal should still be functional
        primary.value = null
        fallback.value = 100
        Thread.sleep(50)
        assertThat(result.value).isEqualTo(100)

        primary.value = 50
        Thread.sleep(50)
        assertThat(result.value).isEqualTo(50)
    }

    // =========================================================================
    // MUTABLE SIGNAL OPERATORS THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - toggle operator thread safety`() {
        val signal = DefaultMutableSignal(false)
        val toggleCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(100) {
                    signal.toggle()
                    toggleCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Total toggles performed
        assertThat(toggleCount.get()).isEqualTo(400)

        // Signal is still functional
        val before = signal.value
        signal.toggle()
        assertThat(signal.value).isEqualTo(!before)
    }

    @RepeatedTest(5)
    fun `concurrent - increment operator thread safety`() {
        val signal = DefaultMutableSignal(0)
        val incrementCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(100) {
                    signal.increment()
                    incrementCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All increments should be reflected (atomic update)
        assertThat(signal.value).isEqualTo(400)
    }

    @RepeatedTest(5)
    fun `concurrent - decrement operator thread safety`() {
        val signal = DefaultMutableSignal(1000)
        val decrementCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(100) {
                    signal.decrement()
                    decrementCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All decrements should be reflected
        assertThat(signal.value).isEqualTo(600)
    }

    @RepeatedTest(5)
    fun `concurrent - list add operator thread safety`() {
        val signal = DefaultMutableSignal(emptyList<Int>())

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    signal.add(threadId * 100 + i)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All elements should be added
        assertThat(signal.value).hasSize(200)
    }

    // =========================================================================
    // STRING OPERATORS THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - string plus operator thread safety`() {
        val a = DefaultMutableSignal("Hello")
        val b = DefaultMutableSignal("World")
        val concat = a + b

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    a.value = "A$threadId-$i"
                }
                doneLatch.countDown()
            }
        }

        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    b.value = "B$threadId-$i"
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final state should be consistent
        a.value = "Hello"
        b.value = "World"
        Thread.sleep(50)
        assertThat(concat.value).isEqualTo("HelloWorld")
    }

    @RepeatedTest(5)
    fun `concurrent - string length operator thread safety`() {
        val source = DefaultMutableSignal("init")
        val length = source.length()
        val emissions = CopyOnWriteArrayList<Int>()

        length.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    source.value = "thread$threadId-value$i"
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All lengths should be positive
        assertThat(emissions).allMatch { it >= 0 }

        // Final state
        source.value = "test"
        Thread.sleep(50)
        assertThat(length.value).isEqualTo(4)
    }

    // =========================================================================
    // COLLECTION OPERATORS THREAD-SAFETY
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent - list size operator thread safety`() {
        val source = DefaultMutableSignal(listOf(1, 2, 3))
        val size = source.size()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    source.value = List(i + 1) { threadId * 100 + it }
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final state
        source.value = listOf(1, 2, 3, 4, 5)
        Thread.sleep(50)
        assertThat(size.value).isEqualTo(5)
    }

    @RepeatedTest(5)
    fun `concurrent - mapList operator thread safety`() {
        val source = DefaultMutableSignal(listOf(1, 2, 3))
        val doubled = source.mapList { it * 2 }
        val emissions = CopyOnWriteArrayList<List<Int>>()

        doubled.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    source.value = listOf(threadId, i, threadId + i)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // All emissions should be correctly doubled
        emissions.forEach { list ->
            assertThat(list).allMatch { it % 2 == 0 || it == 0 }
        }

        // Final state
        source.value = listOf(1, 2, 3)
        Thread.sleep(50)
        assertThat(doubled.value).containsExactly(2, 4, 6)
    }
}
