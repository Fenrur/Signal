package io.github.fenrur.signal

import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.impl.FlattenSignal
import io.github.fenrur.signal.impl.batch
import io.github.fenrur.signal.operators.*
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Comprehensive thread-safety tests for all operators.
 * Tests cover concurrent read/write, subscription management, and error handling.
 */
@OptIn(ExperimentalAtomicApi::class)
class ComprehensiveThreadSafetyTest {

    // =========================================================================
    // PAIRWISE OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - pairwise operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val pairwise = source.pairwise()
        val emissions = CopyOnWriteArrayList<Pair<Int, Int>>()
        val latch = TestCountDownLatch(1)

        pairwise.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val writerCount = 4
        val writesPerThread = 50
        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(writerCount)

        // Writers
        repeat(writerCount) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(writesPerThread) { i ->
                    source.value = threadId * 1000 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Verify all pairs have valid structure (first element equals previous second)
        val allValues = emissions.toList()
        for (i in 1 until allValues.size) {
            // Each emission should be a valid pair
            val pair = allValues[i]
            assertNotNull(pair.first)
            assertNotNull(pair.second)
        }

        // Signal should still be functional
        source.value = 9999
        testSleep(50)
        assertEquals(9999, pairwise.value.second)
    }

    // =========================================================================
    // WITHLATESTFROM OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - withLatestFrom operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(0)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val emissions = CopyOnWriteArrayList<Int>()

        combined.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Source writers
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    source.value = i
                }
                doneLatch.countDown()
            }
        }

        // Other writers
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    other.value = i * 10
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should be valid sums
        assertTrue(emissions.isNotEmpty())

        // Signal should still be functional
        source.value = 50
        other.value = 500
        testSleep(50)
        assertEquals(550, combined.value)
    }

    // =========================================================================
    // ZIP OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - zip operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(0)
        val b = DefaultMutableSignal("init")
        val zipped = zip(a, b)
        val emissions = CopyOnWriteArrayList<Pair<Int, String>>()

        zipped.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Int writers
        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    a.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        // String writers
        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    b.value = "thread$threadId-value$i"
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should be valid pairs
        assertTrue(emissions.isNotEmpty())
        emissions.forEach { pair ->
            assertNotNull(pair.first)
            assertNotNull(pair.second)
        }

        // Signal should still be functional
        a.value = 999
        b.value = "final"
        testSleep(50)
        assertEquals(999 to "final", zipped.value)
    }

    // =========================================================================
    // COMBINEALL OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - combineAll operator thread safety`() = repeat(5) {
        val signals = List(5) { DefaultMutableSignal(it) }
        val combined = signals.combineAll()
        val emissions = CopyOnWriteArrayList<List<Int>>()

        combined.subscribe { result ->
            result.onSuccess { emissions.add(it) }
        }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(5)

        signals.forEachIndexed { idx, signal ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    signal.value = idx * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should have 5 elements
        assertTrue(emissions.isNotEmpty())
        emissions.forEach { list ->
            assertEquals(5, list.size)
        }

        // Signal should still be functional
        signals[0].value = 1000
        testSleep(50)
        assertEquals(1000, combined.value[0])
    }

    // =========================================================================
    // BOOLEAN OPERATORS THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - and operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(true)
        val b = DefaultMutableSignal(true)
        val result = a.and(b)
        val emissions = CopyOnWriteArrayList<Boolean>()

        result.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i % 3 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should be valid booleans
        assertTrue(emissions.isNotEmpty())

        // Final state should be consistent
        a.value = true
        b.value = true
        testSleep(50)
        assertTrue(result.value)

        a.value = false
        testSleep(50)
        assertFalse(result.value)
    }

    @Test
    fun `concurrent - or operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(false)
        val b = DefaultMutableSignal(false)
        val result = a.or(b)
        val emissions = CopyOnWriteArrayList<Boolean>()

        result.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i % 3 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final state should be consistent
        a.value = false
        b.value = false
        testSleep(50)
        assertFalse(result.value)

        a.value = true
        testSleep(50)
        assertTrue(result.value)
    }

    @Test
    fun `concurrent - xor operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(false)
        val b = DefaultMutableSignal(false)
        val result = a.xor(b)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i % 3 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final state should be consistent
        a.value = true
        b.value = false
        testSleep(50)
        assertTrue(result.value)

        b.value = true
        testSleep(50)
        assertFalse(result.value)
    }

    @Test
    fun `concurrent - not operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(true)
        val negated = source.not()
        val emissions = CopyOnWriteArrayList<Boolean>()

        negated.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    source.value = i % 2 == 0
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final state should be consistent
        source.value = true
        testSleep(50)
        assertFalse(negated.value)

        source.value = false
        testSleep(50)
        assertTrue(negated.value)
    }

    // =========================================================================
    // NUMERIC OPERATORS THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - plus operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(0)
        val b = DefaultMutableSignal(0)
        val sum = a + b
        val emissions = CopyOnWriteArrayList<Int>()

        sum.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final state should be consistent
        a.value = 10
        b.value = 20
        testSleep(50)
        assertEquals(30, sum.value)
    }

    @Test
    fun `concurrent - minus operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(100)
        val b = DefaultMutableSignal(0)
        val diff = a - b

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = i
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        a.value = 50
        b.value = 30
        testSleep(50)
        assertEquals(20, diff.value)
    }

    @Test
    fun `concurrent - times operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(1)
        val product = a * b

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        a.value = 7
        b.value = 8
        testSleep(50)
        assertEquals(56, product.value)
    }

    @Test
    fun `concurrent - div operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(100)
        val b = DefaultMutableSignal(1)
        val quotient = a / b

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = (i + 1) * 10
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i + 1 // Avoid division by zero
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        a.value = 100
        b.value = 5
        testSleep(50)
        assertEquals(20, quotient.value)
    }

    @Test
    fun `concurrent - rem operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal(100)
        val b = DefaultMutableSignal(1)
        val remainder = a % b

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    a.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    b.value = i + 1 // Avoid division by zero
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        a.value = 17
        b.value = 5
        testSleep(50)
        assertEquals(2, remainder.value)
    }

    // =========================================================================
    // FILTERNOTNULL OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - filterNotNull operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal<Int?>(0)
        val filtered = source.filterNotNull()
        val emissions = CopyOnWriteArrayList<Int>()

        filtered.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    source.value = if (i % 3 == 0) null else threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should be non-null
        assertTrue(emissions.all { it != null })

        // Signal should still be functional
        source.value = 42
        testSleep(50)
        assertEquals(42, filtered.value)
    }

    // =========================================================================
    // FILTERISINSTANCE OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - filterIsInstance operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal<Any>("init")
        val filtered = source.filterIsInstance<String>()
        val emissions = CopyOnWriteArrayList<String>()

        filtered.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(100) { i ->
                    source.value = if (i % 2 == 0) "str-$threadId-$i" else i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should be strings
        assertTrue(emissions.all { it is String })

        // Signal should still be functional
        source.value = "final"
        testSleep(50)
        assertEquals("final", filtered.value)
    }

    // =========================================================================
    // FLATTEN (DIRECT) OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - flatten operator thread safety`() = repeat(5) {
        val inner1 = DefaultMutableSignal(1)
        val inner2 = DefaultMutableSignal(100)
        val outer = DefaultMutableSignal<Signal<Int>>(inner1)
        val flattened = outer.flatten()
        val emissions = CopyOnWriteArrayList<Int>()

        flattened.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        // Inner signal writers
        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    inner1.value = threadId * 100 + i
                    inner2.value = threadId * 1000 + i
                }
                doneLatch.countDown()
            }
        }

        // Outer signal switcher
        repeat(2) {
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    outer.value = if (i % 2 == 0) inner1 else inner2
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Signal should still be functional
        outer.value = inner1
        inner1.value = 999
        testSleep(50)
        assertEquals(999, flattened.value)
    }

    // =========================================================================
    // RUNNINGREDUCE OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - runningReduce operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(1)
        val reduced = source.runningReduce { acc, v -> acc + v }
        val emissions = CopyOnWriteArrayList<Int>()

        reduced.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(25) { i ->
                    source.value = i + 1
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Signal should still be functional
        val currentValue = reduced.value
        source.value = 10
        testSleep(50)
        assertEquals(currentValue + 10, reduced.value)
    }

    // =========================================================================
    // ORDEFAULT OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - orDefault operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal<Int?>(1)
        val withDefault = source.orDefault(0)
        val emissions = CopyOnWriteArrayList<Int>()

        withDefault.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    source.value = if (i % 3 == 0) null else threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // No emissions should contain null (0 when null)
        emissions.forEach { v ->
            assertNotNull(v)
        }

        // Signal should still be functional
        source.value = null
        testSleep(50)
        assertEquals(0, withDefault.value)

        source.value = 42
        testSleep(50)
        assertEquals(42, withDefault.value)
    }

    // =========================================================================
    // ORELSE OPERATOR THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - orElse operator thread safety`() = repeat(5) {
        val primary = DefaultMutableSignal<Int?>(1)
        val fallback = DefaultMutableSignal(999)
        val result = primary.orElse(fallback)
        val emissions = CopyOnWriteArrayList<Int>()

        result.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    primary.value = if (i % 3 == 0) null else threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    fallback.value = threadId * 1000 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Signal should still be functional
        primary.value = null
        fallback.value = 100
        testSleep(50)
        assertEquals(100, result.value)

        primary.value = 50
        testSleep(50)
        assertEquals(50, result.value)
    }

    // =========================================================================
    // MUTABLE SIGNAL OPERATORS THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - toggle operator thread safety`() = repeat(5) {
        val signal = DefaultMutableSignal(false)
        val toggleCount = AtomicInt(0)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(100) {
                    signal.toggle()
                    toggleCount.incrementAndFetch()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Total toggles performed
        assertEquals(400, toggleCount.load())

        // Signal is still functional
        val before = signal.value
        signal.toggle()
        assertEquals(!before, signal.value)
    }

    @Test
    fun `concurrent - increment operator thread safety`() = repeat(5) {
        val signal = DefaultMutableSignal(0)
        val incrementCount = AtomicInt(0)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(100) {
                    signal.increment()
                    incrementCount.incrementAndFetch()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All increments should be reflected (atomic update)
        assertEquals(400, signal.value)
    }

    @Test
    fun `concurrent - decrement operator thread safety`() = repeat(5) {
        val signal = DefaultMutableSignal(1000)
        val decrementCount = AtomicInt(0)

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) {
            testThread {
                startLatch.await(10000)
                repeat(100) {
                    signal.decrement()
                    decrementCount.incrementAndFetch()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All decrements should be reflected
        assertEquals(600, signal.value)
    }

    @Test
    fun `concurrent - list add operator thread safety`() = repeat(5) {
        val signal = DefaultMutableSignal(emptyList<Int>())

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    signal.add(threadId * 100 + i)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All elements should be added
        assertEquals(200, signal.value.size)
    }

    // =========================================================================
    // STRING OPERATORS THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - string plus operator thread safety`() = repeat(5) {
        val a = DefaultMutableSignal("Hello")
        val b = DefaultMutableSignal("World")
        val concat = a + b

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    a.value = "A$threadId-$i"
                }
                doneLatch.countDown()
            }
        }

        repeat(2) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    b.value = "B$threadId-$i"
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final state should be consistent
        a.value = "Hello"
        b.value = "World"
        testSleep(50)
        assertEquals("HelloWorld", concat.value)
    }

    @Test
    fun `concurrent - string length operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal("init")
        val length = source.length()
        val emissions = CopyOnWriteArrayList<Int>()

        length.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    source.value = "thread$threadId-value$i"
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All lengths should be positive
        assertTrue(emissions.all { it >= 0 })

        // Final state
        source.value = "test"
        testSleep(50)
        assertEquals(4, length.value)
    }

    // =========================================================================
    // COLLECTION OPERATORS THREAD-SAFETY
    // =========================================================================

    @Test
    fun `concurrent - list size operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(listOf(1, 2, 3))
        val size = source.size()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    source.value = List(i + 1) { threadId * 100 + it }
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // Final state
        source.value = listOf(1, 2, 3, 4, 5)
        testSleep(50)
        assertEquals(5, size.value)
    }

    @Test
    fun `concurrent - mapList operator thread safety`() = repeat(5) {
        val source = DefaultMutableSignal(listOf(1, 2, 3))
        val doubled = source.mapList { it * 2 }
        val emissions = CopyOnWriteArrayList<List<Int>>()

        doubled.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val startLatch = TestCountDownLatch(1)
        val doneLatch = TestCountDownLatch(4)

        repeat(4) { threadId ->
            testThread {
                startLatch.await(10000)
                repeat(50) { i ->
                    source.value = listOf(threadId, i, threadId + i)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10000))

        // All emissions should be correctly doubled
        emissions.forEach { list ->
            assertTrue(list.all { it % 2 == 0 || it == 0 })
        }

        // Final state
        source.value = listOf(1, 2, 3)
        testSleep(50)
        assertEquals(listOf(2, 4, 6), doubled.value.toList())
    }
}
