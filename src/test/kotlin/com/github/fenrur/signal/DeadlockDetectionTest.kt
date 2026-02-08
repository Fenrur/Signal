package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for deadlock detection and prevention.
 * Verifies that circular bindings are detected and rejected,
 * and that no deadlock conditions can occur.
 */
class DeadlockDetectionTest {

    // =========================================================================
    // CYCLE DETECTION IN BINDABLE SIGNALS
    // =========================================================================

    @Test
    fun `direct self-binding is rejected`() {
        val source = DefaultMutableSignal(1)
        val signal = bindableSignalOf(source)

        // Cannot bind to self
        assertThatThrownBy {
            signal.bindTo(signal)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cycle")
    }

    @Test
    fun `two-signal cycle is rejected`() {
        val source = DefaultMutableSignal(1)
        val a = bindableSignalOf(source)
        val b = bindableSignalOf(a)  // b -> a

        // a -> b would create cycle (a -> b -> a)
        assertThatThrownBy {
            a.bindTo(b)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cycle")
    }

    @Test
    fun `three-signal cycle is rejected`() {
        val source = DefaultMutableSignal(1)
        val a = bindableSignalOf(source)
        val b = bindableSignalOf(a)  // b -> a
        val c = bindableSignalOf(b)  // c -> b -> a

        // a -> c would create cycle (a -> c -> b -> a)
        assertThatThrownBy {
            a.bindTo(c)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cycle")
    }

    @Test
    fun `long chain cycle is rejected`() {
        val source = DefaultMutableSignal(1)

        // Create chain: source <- s0 <- s1 <- s2 <- ... <- s9
        val signals = mutableListOf<BindableSignal<Int>>()
        signals.add(bindableSignalOf(source))
        for (i in 1 until 10) {
            signals.add(bindableSignalOf(signals[i - 1]))
        }

        // Binding s0 -> s9 would create cycle
        assertThatThrownBy {
            signals[0].bindTo(signals[9])
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cycle")
    }

    @Test
    fun `mutable bindable self-binding is rejected`() {
        val source = DefaultMutableSignal(1)
        val signal = bindableMutableSignalOf(source)

        assertThatThrownBy {
            signal.bindTo(signal)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cycle")
    }

    @Test
    fun `mutable bindable two-signal cycle is rejected`() {
        val source = DefaultMutableSignal(1)
        val a = bindableMutableSignalOf(source)
        val b = bindableMutableSignalOf(a)  // b -> a

        assertThatThrownBy {
            a.bindTo(b)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cycle")
    }

    // =========================================================================
    // VALID BINDING PATTERNS (NO CYCLE)
    // =========================================================================

    @Test
    fun `linear chain is valid`() {
        val source = DefaultMutableSignal(1)
        val a = bindableSignalOf(source)
        val b = bindableSignalOf(a)
        val c = bindableSignalOf(b)

        assertThat(c.value).isEqualTo(1)

        source.value = 42
        assertThat(c.value).isEqualTo(42)
    }

    @Test
    fun `rebinding to different signal is valid`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val bindable = bindableSignalOf(source1)

        assertThat(bindable.value).isEqualTo(1)

        bindable.bindTo(source2)
        assertThat(bindable.value).isEqualTo(2)
    }

    @Test
    fun `diamond without cycle is valid`() {
        //     source
        //      / \
        //     a   b
        //      \ /
        //       c (non-bindable combine, not cycle)
        val source = DefaultMutableSignal(1)
        val a = bindableSignalOf(source)
        val b = bindableSignalOf(source)
        val c = combine(a, b) { x, y -> x + y }

        assertThat(c.value).isEqualTo(2)

        source.value = 10
        assertThat(c.value).isEqualTo(20)
    }

    @Test
    fun `switching binding target is valid`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val a = bindableSignalOf(source1)
        val b = bindableSignalOf(source2)

        // Switch a to point to source2
        a.bindTo(source2)
        assertThat(a.value).isEqualTo(2)

        // b can still bind to source1
        b.bindTo(source1)
        assertThat(b.value).isEqualTo(1)
    }

    // =========================================================================
    // NO DEADLOCK UNDER CONCURRENT OPERATIONS
    // =========================================================================

    @RepeatedTest(5)
    fun `no deadlock with concurrent subscribe and update`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Subscribe/unsubscribe thread
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) {
                    val unsub = mapped.subscribe { }
                    unsub()
                }
                doneLatch.countDown()
            }
        }

        // Update thread
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) { i ->
                    source.value = i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()
    }

    @RepeatedTest(5)
    fun `no deadlock with concurrent batch operations`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
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
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()
    }

    @RepeatedTest(5)
    fun `no deadlock with nested signal access during update`() {
        val inner = DefaultMutableSignal(0)
        val outer = DefaultMutableSignal<Signal<Int>>(inner)
        val flattened = outer.flatten()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Inner updates
        repeat(2) { threadId ->
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    inner.value = threadId * 100 + i
                }
                doneLatch.countDown()
            }
        }

        // Flattened reads
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(50) {
                    flattened.value
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()
    }

    @RepeatedTest(5)
    fun `no deadlock with listener modifying source`() {
        val source = DefaultMutableSignal(0)
        val updates = AtomicInteger(0)

        // Listener that modifies the source (recursive update)
        source.subscribe { r ->
            r.onSuccess { v ->
                if (v < 5) {
                    // This triggers a recursive update
                    source.update { it + 1 }
                }
                updates.incrementAndGet()
            }
        }

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(10) {
                    source.value = 0
                    Thread.sleep(10)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()
    }

    // =========================================================================
    // COMPLEX CONCURRENT SCENARIOS
    // =========================================================================

    @RepeatedTest(5)
    fun `no deadlock with combined signal diamond and concurrent updates`() {
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = a.map { it * 3 }
        val d = combine(b, c) { x, y -> x + y }

        val emissions = CopyOnWriteArrayList<Int>()
        d.subscribe { r -> r.onSuccess { emissions.add(it) } }

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                startLatch.await()
                repeat(50) { i ->
                    a.value = i
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()

        // Verify consistency
        a.value = 10
        Thread.sleep(50)
        assertThat(d.value).isEqualTo(50)  // 10*2 + 10*3
    }

    @RepeatedTest(5)
    fun `no deadlock with rebinding during updates`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(100)
        val bindable = bindableSignalOf(source1)

        val emissions = CopyOnWriteArrayList<Int>()
        bindable.subscribe { r -> r.onSuccess { emissions.add(it) } }

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Source1 updates
        executor.submit {
            startLatch.await()
            repeat(50) { i ->
                source1.value = i
            }
            doneLatch.countDown()
        }

        // Source2 updates
        executor.submit {
            startLatch.await()
            repeat(50) { i ->
                source2.value = i + 100
            }
            doneLatch.countDown()
        }

        // Rebinding thread
        executor.submit {
            startLatch.await()
            repeat(25) {
                bindable.bindTo(source1)
                Thread.sleep(1)
                bindable.bindTo(source2)
                Thread.sleep(1)
            }
            doneLatch.countDown()
        }

        // Reader thread
        executor.submit {
            startLatch.await()
            repeat(100) {
                bindable.value
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()
    }

    // =========================================================================
    // CONCURRENT BINDING SCENARIOS
    // =========================================================================

    @RepeatedTest(5)
    fun `concurrent rebinding is safe`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val source3 = DefaultMutableSignal(3)
        val bindable = bindableSignalOf(source1)

        val executor = Executors.newFixedThreadPool(3)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(3)

        executor.submit {
            startLatch.await()
            repeat(50) {
                bindable.bindTo(source1)
            }
            doneLatch.countDown()
        }

        executor.submit {
            startLatch.await()
            repeat(50) {
                bindable.bindTo(source2)
            }
            doneLatch.countDown()
        }

        executor.submit {
            startLatch.await()
            repeat(50) {
                bindable.bindTo(source3)
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()

        // Bindable should be bound to one of the sources
        assertThat(bindable.value).isIn(1, 2, 3)
    }

    @RepeatedTest(5)
    fun `concurrent read during rebind is safe`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val bindable = bindableSignalOf(source1)

        val values = CopyOnWriteArrayList<Int>()

        val executor = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)

        // Rebind threads
        repeat(2) { idx ->
            executor.submit {
                startLatch.await()
                repeat(100) {
                    bindable.bindTo(if (idx == 0) source1 else source2)
                }
                doneLatch.countDown()
            }
        }

        // Read threads
        repeat(2) {
            executor.submit {
                startLatch.await()
                repeat(100) {
                    values.add(bindable.value)
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(finished).isTrue()

        // All values should be either 1 or 2
        assertThat(values).allMatch { it == 1 || it == 2 }
    }
}
