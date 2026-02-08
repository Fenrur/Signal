package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Stress tests for the Signal library.
 * Tests large-scale usage, deep chains, and high concurrency scenarios.
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

        val emissions = CopyOnWriteArrayList<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        // Initial value: 0 + 100 = 100
        assertThat(chain.value).isEqualTo(100)

        emissions.clear()

        // Update source
        source.value = 10
        assertThat(chain.value).isEqualTo(110)
        assertThat(emissions).contains(110)
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

        val emissions = CopyOnWriteArrayList<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }

        emissions.clear()

        source.value = 5
        // Each map { it + 1 } adds 1, there are 17 of them (50/3 rounded)
        assertThat(chain.value).isGreaterThan(5)
        assertThat(emissions).isNotEmpty()
    }

    @RepeatedTest(3)
    fun `deep chain with concurrent updates`() {
        val source = DefaultMutableSignal(0)

        var chain: Signal<Int> = source
        repeat(50) {
            chain = chain.map { it + 1 }
        }

        val emissions = CopyOnWriteArrayList<Int>()
        chain.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(25) { i ->
                    source.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // Chain should still be functional
        source.value = 100
        Thread.sleep(100)
        assertThat(chain.value).isEqualTo(150)  // 100 + 50
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

        val emissions = List(50) { CopyOnWriteArrayList<Int>() }
        derived.forEachIndexed { idx, signal ->
            signal.subscribe { r -> r.onSuccess { emissions[idx].add(it) } }
        }

        // Clear initial emissions
        emissions.forEach { it.clear() }

        // Update source
        source.value = 100

        // All derived signals should update
        derived.forEachIndexed { idx, signal ->
            assertThat(signal.value).isEqualTo(100 + idx)
            assertThat(emissions[idx]).contains(100 + idx)
        }
    }

    @Test
    fun `wide graph - combine many signals`() {
        val signals = List(20) { i -> DefaultMutableSignal(i) }
        val combined = signals.combineAll()

        val emissions = CopyOnWriteArrayList<List<Int>>()
        combined.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        // Update each signal
        signals.forEachIndexed { idx, signal ->
            signal.value = idx * 10
        }

        // Final value should reflect all updates
        assertThat(combined.value).hasSize(20)
        combined.value.forEachIndexed { idx, value ->
            assertThat(value).isEqualTo(idx * 10)
        }
    }

    @RepeatedTest(3)
    fun `wide graph with concurrent updates`() {
        val sources = List(20) { i -> DefaultMutableSignal(i) }
        val combined = sources.combineAll()

        val emissions = CopyOnWriteArrayList<List<Int>>()
        combined.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        val executor = Executors.newFixedThreadPool(20)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(20)

        sources.forEachIndexed { idx, signal ->
            executor.submit {
                startLatch.await()
                repeat(10) { i ->
                    signal.value = idx * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // Combined signal should still be functional
        sources[0].value = 999
        Thread.sleep(100)
        assertThat(combined.value[0]).isEqualTo(999)
    }

    // =========================================================================
    // HIGH THROUGHPUT SCENARIOS
    // =========================================================================

    @RepeatedTest(3)
    fun `high throughput - 10000 updates single signal`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val updateCount = AtomicInteger(0)
        mapped.subscribe { r ->
            r.onSuccess { updateCount.incrementAndGet() }
        }

        val startTime = System.currentTimeMillis()

        repeat(10000) { i ->
            source.value = i
        }

        val duration = System.currentTimeMillis() - startTime

        // Should complete in reasonable time (< 5 seconds)
        assertThat(duration).isLessThan(5000)

        // Final value should be correct
        assertThat(mapped.value).isEqualTo(19998)  // 9999 * 2
    }

    @RepeatedTest(3)
    fun `high throughput - concurrent updates with multiple readers`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val readCount = AtomicLong(0)
        val writeCount = AtomicLong(0)

        val executor = Executors.newFixedThreadPool(8)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(8)

        // 4 writers
        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(1000) { i ->
                    source.value = i
                    writeCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        // 4 readers
        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(1000) {
                    mapped.value
                    readCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(writeCount.get()).isEqualTo(4000)
        assertThat(readCount.get()).isEqualTo(4000)

        // Signal should be consistent
        source.value = 50
        assertThat(mapped.value).isEqualTo(100)
    }

    // =========================================================================
    // LARGE BATCH OPERATIONS
    // =========================================================================

    @Test
    fun `large batch - 1000 updates in single batch`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val emissions = CopyOnWriteArrayList<Int>()
        mapped.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        batch {
            repeat(1000) { i ->
                source.value = i
            }
        }

        // Only final value should be emitted
        assertThat(emissions).hasSize(1)
        assertThat(emissions[0]).isEqualTo(1998)  // 999 * 2
    }

    @Test
    fun `large batch with combined signals`() {
        val signals = List(10) { i -> DefaultMutableSignal(i) }
        val combined = signals.combineAll()

        val emissions = CopyOnWriteArrayList<List<Int>>()
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
        assertThat(emissions).hasSize(1)
        assertThat(emissions[0]).hasSize(10)
    }

    // =========================================================================
    // MANY SUBSCRIBERS
    // =========================================================================

    @Test
    fun `many subscribers - 100 listeners on single signal`() {
        val source = DefaultMutableSignal(0)

        val emissions = List(100) { CopyOnWriteArrayList<Int>() }
        val unsubscribers = List(100) { idx ->
            source.subscribe { r -> r.onSuccess { emissions[idx].add(it) } }
        }

        emissions.forEach { it.clear() }

        source.value = 42

        // All listeners should receive the update
        emissions.forEach { list ->
            assertThat(list).contains(42)
        }

        // Unsubscribe all
        unsubscribers.forEach { it.invoke() }

        // No more emissions after unsubscribe
        emissions.forEach { it.clear() }
        source.value = 100

        emissions.forEach { list ->
            assertThat(list).isEmpty()
        }
    }

    @RepeatedTest(3)
    fun `many subscribers - concurrent subscribe and unsubscribe`() {
        val source = DefaultMutableSignal(0)
        val subscribeCount = AtomicInteger(0)
        val unsubscribeCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(8)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(8)

        // 4 threads subscribing and unsubscribing
        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(100) {
                    val unsub = source.subscribe { }
                    subscribeCount.incrementAndGet()
                    unsub()
                    unsubscribeCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        // 4 threads updating
        repeat(4) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    source.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(subscribeCount.get()).isEqualTo(400)
        assertThat(unsubscribeCount.get()).isEqualTo(400)

        // Signal should be functional
        source.value = 999
        assertThat(source.value).isEqualTo(999)
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

        val emissions = CopyOnWriteArrayList<Int>()
        final.subscribe { r -> r.onSuccess { emissions.add(it) } }
        emissions.clear()

        source.value = 10

        // Single emission with consistent value
        assertThat(emissions).hasSize(1)
    }

    @RepeatedTest(3)
    fun `complex diamond with concurrent updates`() {
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

        val executor = Executors.newFixedThreadPool(5)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(5)

        sources.forEachIndexed { idx, signal ->
            executor.submit {
                startLatch.await()
                repeat(20) { i ->
                    signal.value = idx * 100 + i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Final signal should be consistent
        sources.forEachIndexed { idx, signal ->
            signal.value = idx * 10
        }
        Thread.sleep(100)

        // 0+10 + 10+20 + 20+30 + 30+40 = 10 + 30 + 50 + 70 = 160
        assertThat(final.value).isEqualTo(160)
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
        System.gc()
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
        System.gc()
    }
}
