package com.github.fenrur.signal

import com.github.fenrur.signal.impl.*
import com.github.fenrur.signal.operators.*
import kotlin.test.*

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
        val values = mutableListOf<Int>()

        val unsubscribe = source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        source.value = 20
        assertEquals(listOf(10, 20), values.toList())

        unsubscribe()

        source.value = 30
        source.value = 40

        // No new values after unsubscribe
        assertEquals(listOf(10, 20), values.toList())
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
        val values = mutableListOf<Int>()
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
        assertEquals(listOf(10, 20), values.toList())
    }

    // =========================================================================
    // LAZY SUBSCRIPTION TESTS
    // =========================================================================

    @Test
    fun `computed signal does not subscribe until needed`() {
        var subscribeCount = 0
        val source = object : Signal<Int> {
            override val value: Int = 10
            override val isClosed: Boolean = false
            override fun subscribe(listener: SubscribeListener<Int>): UnSubscriber {
                subscribeCount++
                listener(Result.success(value))
                return { }
            }
            override fun close() {}
        }

        // Create mapped signal but don't subscribe
        val mapped = MappedSignal(source) { it * 2 }

        // Reading value doesn't subscribe (just pulls)
        assertEquals(20, mapped.value)
        // No subscription yet - lazy subscription only subscribes when there are listeners

        // Subscribe to mapped
        val unsub = mapped.subscribe { }
        assertTrue(subscribeCount > 0)

        // Unsubscribe
        unsub()
    }

    @Test
    fun `computed signal unsubscribes when all listeners removed`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = mutableListOf<Int>()

        // First subscription
        val unsub1 = mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Second subscription
        val unsub2 = mapped.subscribe { result ->
            result.onSuccess { values.add(it * 10) }
        }

        source.value = 20
        assertTrue(values.contains(20))
        assertTrue(values.contains(200))
        assertTrue(values.contains(40))
        assertTrue(values.contains(400))

        // Remove first subscription
        unsub1()
        values.clear()

        source.value = 30
        // Only second subscription receives
        assertEquals(listOf(600), values.toList())

        // Remove second subscription
        unsub2()
        values.clear()

        source.value = 40
        // No subscriptions, no values
        assertTrue(values.isEmpty())

        // But value pull still works
        assertEquals(80, mapped.value)
    }

    // =========================================================================
    // CLOSE CLEANUP TESTS
    // =========================================================================

    @Test
    fun `close clears all listeners`() {
        val source = DefaultMutableSignal(10)
        val values = mutableListOf<Int>()

        source.subscribe { result -> result.onSuccess { values.add(it) } }
        source.subscribe { result -> result.onSuccess { values.add(it * 10) } }

        source.value = 20
        assertTrue(values.contains(10))
        assertTrue(values.contains(100))
        assertTrue(values.contains(20))
        assertTrue(values.contains(200))

        source.close()
        values.clear()

        source.value = 30
        assertTrue(values.isEmpty())
    }

    @Test
    fun `close on computed signal unsubscribes from source`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = mutableListOf<Int>()

        mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        source.value = 20
        assertEquals(listOf(20, 40), values.toList())

        mapped.close()
        values.clear()

        source.value = 30
        assertTrue(values.isEmpty())
    }

    @Test
    fun `close is idempotent`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }

        mapped.close()
        assertTrue(mapped.isClosed)

        // Multiple closes don't throw
        mapped.close()
        mapped.close()

        assertTrue(mapped.isClosed)
    }

    @Test
    fun `subscribe after close returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(10)
        source.close()

        val values = mutableListOf<Int>()
        val unsub = source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // No values received
        assertTrue(values.isEmpty())

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
        val values = mutableListOf<Int>()

        mapped2.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        source.value = 20
        assertEquals(listOf(21, 41), values.toList())

        // Close intermediate signal
        mapped1.close()
        values.clear()

        source.value = 30
        // Chain is broken, no new values
        assertTrue(values.isEmpty())
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

        val values = mutableListOf<Int>()
        d.subscribe { result -> result.onSuccess { values.add(it) } }

        a.value = 2
        assertEquals(listOf(5, 10), values.toList())

        // Close source
        a.close()
        values.clear()

        a.value = 3
        assertTrue(values.isEmpty())
    }

    // =========================================================================
    // BINDABLE SIGNAL CLEANUP TESTS
    // =========================================================================

    @Test
    fun `bindable signal cleanup on rebind`() {
        val signal1 = DefaultMutableSignal(10)
        val signal2 = DefaultMutableSignal(20)
        val bindable = DefaultBindableSignal(signal1)

        val values = mutableListOf<Int>()
        bindable.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        signal1.value = 15
        assertTrue(values.contains(10))
        assertTrue(values.contains(15))

        // Rebind to signal2 - this notifies with signal2's value
        bindable.bindTo(signal2)

        // Clear and test that only signal2 updates propagate
        values.clear()
        signal1.value = 100
        signal2.value = 25

        // Only signal2's update should be in values
        assertTrue(values.contains(25))
        assertFalse(values.contains(100))
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
        assertTrue(signal1.isClosed)
        assertFalse(signal2.isClosed)
    }

    @Test
    fun `bindable signal with takeOwnership closes signal on close`() {
        val signal = DefaultMutableSignal(10)
        val bindable = DefaultBindableSignal(signal, takeOwnership = true)

        assertFalse(signal.isClosed)

        bindable.close()

        assertTrue(signal.isClosed)
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

        val values = mutableListOf<Int>()
        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        inner1.value = 15
        assertTrue(values.contains(10))
        assertTrue(values.contains(15))

        // Switch to inner2 - this notifies with inner2's value
        outer.value = inner2

        // Clear and test that only inner2 updates propagate
        values.clear()
        inner1.value = 100
        inner2.value = 25

        // Only inner2's update should be in values
        assertTrue(values.contains(25))
        assertFalse(values.contains(100))
    }

    @Test
    fun `flatten signal cleanup on close`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal<Signal<Int>>(inner)
        val flattened = FlattenSignal(outer)

        val values = mutableListOf<Int>()
        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        inner.value = 15
        assertEquals(listOf(10, 15), values.toList())

        flattened.close()
        values.clear()

        inner.value = 20
        assertTrue(values.isEmpty())
    }

    // =========================================================================
    // RE-SUBSCRIPTION TESTS
    // =========================================================================

    @Test
    fun `re-subscribe after unsubscribe works correctly`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }
        val values = mutableListOf<Int>()

        // First subscription cycle
        val unsub1 = mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }
        source.value = 20
        unsub1()

        // Initial: 10*2=20, Update: 20*2=40
        assertEquals(listOf(20, 40), values.toList())
        values.clear()

        // Second subscription cycle
        // Current source value is 20, so mapped = 40
        val unsub2 = mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }
        source.value = 30
        unsub2()

        // Gets current value on subscribe (40), then update (60)
        assertEquals(listOf(40, 60), values.toList())
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
        assertEquals(1998, mapped.value)
    }

    // =========================================================================
    // BATCH WITH CLEANUP
    // =========================================================================

    @Test
    fun `unsubscribe during batch is handled correctly`() {
        val source = DefaultMutableSignal(0)
        val values = mutableListOf<Int>()
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
        assertTrue(values.contains(0))
        // May or may not see final batch value depending on timing
    }

    @Test
    fun `close during batch is handled correctly`() {
        val source = DefaultMutableSignal(0)
        val values = mutableListOf<Int>()

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
        assertTrue(source.isClosed)
        assertTrue(values.contains(0))
        assertTrue(values.contains(3))
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
    }

    @Test
    fun `chain reference pattern - verify close allows potential cleanup`() {
        val source = DefaultMutableSignal(1)
        var chain: Signal<Int>? = source.map { it * 2 }.map { it + 1 }

        chain?.subscribe { }
        chain?.close()
        chain = null

        // Chain should be eligible for GC
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
    }
}
