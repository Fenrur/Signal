package com.github.fenrur.signal

import com.github.fenrur.signal.impl.*
import com.github.fenrur.signal.operators.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for resource cleanup, subscription management, and memory leak prevention.
 *
 * These tests verify that:
 * - Unsubscribing properly releases resources
 * - Lazy subscription works correctly
 * - Closed signals don't hold references
 * - Multiple subscribe/unsubscribe cycles work correctly
 */
class ResourceCleanupTest {

    // =========================================================================
    // BASIC UNSUBSCRIBE TESTS
    // =========================================================================

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(10)
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        source.value = 20
        assertThat(values).containsExactly(10, 20)

        unsubscribe()

        source.value = 30
        source.value = 40

        // No new values after unsubscribe
        assertThat(values).containsExactly(10, 20)
    }

    @Test
    fun `multiple unsubscribe calls are safe`() {
        val source = DefaultMutableSignal(10)
        val unsubscribe = source.subscribe { }

        // Multiple calls should not throw
        unsubscribe()
        unsubscribe()
        unsubscribe()
    }

    @Test
    fun `unsubscribe during notification is safe`() {
        val source = DefaultMutableSignal(10)
        val values = CopyOnWriteArrayList<Int>()
        lateinit var unsub: UnSubscriber

        unsub = source.subscribe { result ->
            result.onSuccess { v ->
                values.add(v)
                if (v == 20) unsub() // Unsubscribe during notification
            }
        }

        source.value = 20
        source.value = 30

        // Got initial and 20, but not 30
        assertThat(values).containsExactly(10, 20)
    }

    // =========================================================================
    // LAZY SUBSCRIPTION TESTS
    // =========================================================================

    @Test
    fun `computed signal does not subscribe until needed`() {
        val subscribeCount = AtomicInteger(0)
        val source = object : Signal<Int> {
            override val value: Int = 10
            override val isClosed: Boolean = false
            override fun subscribe(listener: SubscribeListener<Int>): UnSubscriber {
                subscribeCount.incrementAndGet()
                listener(Result.success(value))
                return { }
            }
            override fun close() {}
        }

        // Create mapped signal but don't subscribe
        val mapped = MappedSignal(source) { it * 2 }

        // Reading value doesn't subscribe (just pulls)
        assertThat(mapped.value).isEqualTo(20)
        // No subscription yet - lazy subscription only subscribes when there are listeners

        // Subscribe to mapped
        val unsub = mapped.subscribe { }
        assertThat(subscribeCount.get()).isGreaterThan(0)

        // Unsubscribe
        unsub()
    }

    @Test
    fun `computed signal unsubscribes when all listeners removed`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        // First subscription
        val unsub1 = mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Second subscription
        val unsub2 = mapped.subscribe { result ->
            result.onSuccess { values.add(it * 10) }
        }

        source.value = 20
        assertThat(values).contains(20, 200, 40, 400)

        // Remove first subscription
        unsub1()
        values.clear()

        source.value = 30
        // Only second subscription receives
        assertThat(values).containsExactly(600)

        // Remove second subscription
        unsub2()
        values.clear()

        source.value = 40
        // No subscriptions, no values
        assertThat(values).isEmpty()

        // But value pull still works
        assertThat(mapped.value).isEqualTo(80)
    }

    // =========================================================================
    // CLOSE CLEANUP TESTS
    // =========================================================================

    @Test
    fun `close clears all listeners`() {
        val source = DefaultMutableSignal(10)
        val values = CopyOnWriteArrayList<Int>()

        source.subscribe { result -> result.onSuccess { values.add(it) } }
        source.subscribe { result -> result.onSuccess { values.add(it * 10) } }

        source.value = 20
        assertThat(values).contains(10, 100, 20, 200)

        source.close()
        values.clear()

        source.value = 30
        assertThat(values).isEmpty()
    }

    @Test
    fun `close on computed signal unsubscribes from source`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        source.value = 20
        assertThat(values).containsExactly(20, 40)

        mapped.close()
        values.clear()

        source.value = 30
        assertThat(values).isEmpty()
    }

    @Test
    fun `close is idempotent`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }

        mapped.close()
        assertThat(mapped.isClosed).isTrue()

        // Multiple closes don't throw
        mapped.close()
        mapped.close()

        assertThat(mapped.isClosed).isTrue()
    }

    @Test
    fun `subscribe after close returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(10)
        source.close()

        val values = CopyOnWriteArrayList<Int>()
        val unsub = source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // No values received
        assertThat(values).isEmpty()

        // Unsubscribe doesn't throw
        unsub()
    }

    // =========================================================================
    // CHAIN CLEANUP TESTS
    // =========================================================================

    @Test
    fun `closing intermediate signal breaks chain`() {
        val source = DefaultMutableSignal(10)
        val mapped1 = source.map { it * 2 }
        val mapped2 = mapped1.map { it + 1 }
        val values = CopyOnWriteArrayList<Int>()

        mapped2.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        source.value = 20
        assertThat(values).containsExactly(21, 41)

        // Close intermediate signal
        mapped1.close()
        values.clear()

        source.value = 30
        // Chain is broken, no new values
        assertThat(values).isEmpty()
    }

    @Test
    fun `diamond pattern cleanup on source close`() {
        //     a
        //    / \
        //   b   c
        //    \ /
        //     d
        val a = DefaultMutableSignal(1)
        val b = a.map { it * 2 }
        val c = a.map { it * 3 }
        val d = combine(b, c) { x, y -> x + y }

        val values = CopyOnWriteArrayList<Int>()
        d.subscribe { result -> result.onSuccess { values.add(it) } }

        a.value = 2
        assertThat(values).containsExactly(5, 10)

        // Close source
        a.close()
        values.clear()

        a.value = 3
        assertThat(values).isEmpty()
    }

    // =========================================================================
    // BINDABLE SIGNAL CLEANUP TESTS
    // =========================================================================

    @Test
    fun `bindable signal cleanup on rebind`() {
        val signal1 = DefaultMutableSignal(10)
        val signal2 = DefaultMutableSignal(20)
        val bindable = DefaultBindableSignal(signal1)

        val values = CopyOnWriteArrayList<Int>()
        bindable.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        signal1.value = 15
        assertThat(values).contains(10, 15)

        // Rebind to signal2 - this notifies with signal2's value
        bindable.bindTo(signal2)

        // Clear and test that only signal2 updates propagate
        values.clear()
        signal1.value = 100
        signal2.value = 25

        // Only signal2's update should be in values
        assertThat(values).contains(25)
        assertThat(values).doesNotContain(100)
    }

    @Test
    fun `bindable signal with takeOwnership closes old signal on rebind`() {
        val signal1 = DefaultMutableSignal(10)
        val signal2 = DefaultMutableSignal(20)
        val bindable = DefaultBindableSignal(signal1, takeOwnership = true)

        bindable.subscribe { }

        // Rebind
        bindable.bindTo(signal2)

        // signal1 is closed
        assertThat(signal1.isClosed).isTrue()
        assertThat(signal2.isClosed).isFalse()
    }

    @Test
    fun `bindable signal with takeOwnership closes signal on close`() {
        val signal = DefaultMutableSignal(10)
        val bindable = DefaultBindableSignal(signal, takeOwnership = true)

        assertThat(signal.isClosed).isFalse()

        bindable.close()

        assertThat(signal.isClosed).isTrue()
    }

    // =========================================================================
    // FLATTEN SIGNAL CLEANUP TESTS
    // =========================================================================

    @Test
    fun `flatten signal cleanup on inner signal switch`() {
        val inner1 = DefaultMutableSignal(10)
        val inner2 = DefaultMutableSignal(20)
        val outer = DefaultMutableSignal<Signal<Int>>(inner1)
        val flattened = FlattenSignal(outer)

        val values = CopyOnWriteArrayList<Int>()
        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        inner1.value = 15
        assertThat(values).contains(10, 15)

        // Switch to inner2 - this notifies with inner2's value
        outer.value = inner2

        // Clear and test that only inner2 updates propagate
        values.clear()
        inner1.value = 100
        inner2.value = 25

        // Only inner2's update should be in values
        assertThat(values).contains(25)
        assertThat(values).doesNotContain(100)
    }

    @Test
    fun `flatten signal cleanup on close`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal<Signal<Int>>(inner)
        val flattened = FlattenSignal(outer)

        val values = CopyOnWriteArrayList<Int>()
        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        inner.value = 15
        assertThat(values).containsExactly(10, 15)

        flattened.close()
        values.clear()

        inner.value = 20
        assertThat(values).isEmpty()
    }

    // =========================================================================
    // CONCURRENT SUBSCRIBE/UNSUBSCRIBE
    // =========================================================================

    @RepeatedTest(10)
    fun `concurrent subscribe and unsubscribe is thread-safe`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }
        val latch = CountDownLatch(1)
        val unsubscribers = java.util.concurrent.ConcurrentLinkedQueue<UnSubscriber>()
        val threads = mutableListOf<Thread>()

        // Threads that subscribe
        repeat(10) {
            threads += Thread {
                latch.await()
                repeat(50) {
                    val unsub = mapped.subscribe { }
                    unsubscribers.offer(unsub)
                    Thread.yield()
                }
            }
        }

        // Threads that unsubscribe
        repeat(5) {
            threads += Thread {
                latch.await()
                repeat(100) {
                    val unsub = unsubscribers.poll()
                    unsub?.invoke()
                    Thread.yield()
                }
            }
        }

        // Thread that updates
        threads += Thread {
            latch.await()
            repeat(100) { i ->
                source.value = i
                Thread.yield()
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }

        // Cleanup remaining subscriptions
        while (true) {
            val unsub = unsubscribers.poll() ?: break
            unsub()
        }

        // Signal still works
        source.value = 999
        assertThat(mapped.value).isEqualTo(1998)
    }

    @RepeatedTest(10)
    fun `concurrent close is thread-safe`() {
        val source = DefaultMutableSignal(0)
        val signals = (1..10).map { source.map { v -> v * it } }
        val latch = CountDownLatch(1)
        val threads = mutableListOf<Thread>()

        // Subscribe to all
        signals.forEach { signal ->
            signal.subscribe { }
        }

        // Concurrent close
        signals.forEach { signal ->
            threads += Thread {
                latch.await()
                signal.close()
            }
        }

        // Concurrent value updates
        threads += Thread {
            latch.await()
            repeat(100) { i ->
                source.value = i
                Thread.yield()
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }

        // All signals are closed
        signals.forEach { assertThat(it.isClosed).isTrue() }
    }

    // =========================================================================
    // RE-SUBSCRIPTION TESTS
    // =========================================================================

    @Test
    fun `re-subscribe after unsubscribe works correctly`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        // First subscription cycle
        val unsub1 = mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }
        source.value = 20
        unsub1()

        // Initial: 10*2=20, Update: 20*2=40
        assertThat(values).containsExactly(20, 40)
        values.clear()

        // Second subscription cycle
        // Current source value is 20, so mapped = 40
        val unsub2 = mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }
        source.value = 30
        unsub2()

        // Gets current value on subscribe (40), then update (60)
        assertThat(values).containsExactly(40, 60)
    }

    @Test
    fun `rapid subscribe unsubscribe cycles are stable`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { it * 2 }

        repeat(1000) { i ->
            val unsub = mapped.subscribe { }
            source.value = i
            unsub()
        }

        // Signal still works
        source.value = 999
        assertThat(mapped.value).isEqualTo(1998)
    }

    // =========================================================================
    // BATCH WITH CLEANUP
    // =========================================================================

    @Test
    fun `unsubscribe during batch is handled correctly`() {
        val source = DefaultMutableSignal(0)
        val values = CopyOnWriteArrayList<Int>()
        lateinit var unsub: UnSubscriber

        unsub = source.subscribe { result ->
            result.onSuccess { v ->
                values.add(v)
                if (v == 1) unsub() // Unsubscribe during batch
            }
        }

        batch {
            source.value = 1
            source.value = 2
            source.value = 3
        }

        // Unsubscribed after seeing 1
        assertThat(values).contains(0)
        // May or may not see final batch value depending on timing
    }

    @Test
    fun `close during batch is handled correctly`() {
        val source = DefaultMutableSignal(0)
        val values = CopyOnWriteArrayList<Int>()

        source.subscribe { result ->
            result.onSuccess { v ->
                values.add(v)
                // Close when we see the final batch value
                if (v == 3) source.close()
            }
        }

        batch {
            source.value = 1
            source.value = 2
            source.value = 3
        }

        // Listener sees final batch value (3) and closes
        assertThat(source.isClosed).isTrue()
        assertThat(values).contains(0, 3)
    }

    // =========================================================================
    // WEAK REFERENCE PATTERNS (GC BEST EFFORT)
    // =========================================================================

    @Test
    fun `signal reference pattern - verify unsubscribe allows potential cleanup`() {
        val signal = DefaultMutableSignal(1)

        // Create and immediately release a reference
        var ref: (() -> Unit)? = signal.subscribe { }
        ref?.invoke() // Unsubscribe
        ref = null

        // At this point, the listener should be eligible for GC
        // We don't assert GC because it's non-deterministic
        System.gc()
    }

    @Test
    fun `chain reference pattern - verify close allows potential cleanup`() {
        val source = DefaultMutableSignal(1)
        var chain: Signal<Int>? = source.map { it * 2 }.map { it + 1 }

        chain?.subscribe { }
        chain?.close()
        chain = null

        // Chain should be eligible for GC
        System.gc()
    }

    @Test
    fun `deep chain pattern - verify cleanup after unsubscribe`() {
        val source = DefaultMutableSignal(1)

        var chain: Signal<Int>? = source
        repeat(20) {
            chain = chain!!.map { it + 1 }
        }

        val unsub = chain!!.subscribe { }
        unsub()
        chain = null

        // Deep chain should be eligible for GC
        System.gc()
    }
}
