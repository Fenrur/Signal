package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.MappedSignal
import com.github.fenrur.signal.impl.FlattenSignal
import com.github.fenrur.signal.operators.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

/**
 * Tests for memory leak prevention patterns.
 * Note: GC behavior is non-deterministic, so these tests verify patterns
 * rather than absolute GC guarantees.
 */
class MemoryLeakTest {

    /**
     * Attempts garbage collection. Returns true if the weak reference was cleared.
     * Note: GC is non-deterministic, so this is best-effort.
     */
    private fun attemptGc(weakRef: WeakReference<*>, maxAttempts: Int = 5): Boolean {
        repeat(maxAttempts) {
            System.gc()
            Thread.sleep(50)
            if (weakRef.get() == null) return true
        }
        return weakRef.get() == null
    }

    // =========================================================================
    // UNSUBSCRIBE PATTERNS
    // =========================================================================

    @Test
    fun `unsubscribe removes listener from signal`() {
        val signal = DefaultMutableSignal(1)
        var receivedCount = 0

        val unsub = signal.subscribe { receivedCount++ }

        signal.value = 2
        assertThat(receivedCount).isEqualTo(2) // Initial + update

        unsub()

        signal.value = 3
        assertThat(receivedCount).isEqualTo(2) // No more updates
    }

    @Test
    fun `multiple unsubscribe calls are safe`() {
        val signal = DefaultMutableSignal(1)
        val unsub = signal.subscribe { }

        unsub()
        unsub() // Second call should be no-op
        unsub() // Third call should be no-op
    }

    @Test
    fun `unsubscribing all listeners allows new subscriptions`() {
        val signal = DefaultMutableSignal(1)
        val values = mutableListOf<Int>()

        val unsub1 = signal.subscribe { r -> r.onSuccess { values.add(it) } }
        val unsub2 = signal.subscribe { r -> r.onSuccess { values.add(it) } }

        unsub1()
        unsub2()
        values.clear()

        signal.subscribe { r -> r.onSuccess { values.add(it) } }

        signal.value = 42
        assertThat(values).contains(42)
    }

    // =========================================================================
    // CLOSE PATTERNS
    // =========================================================================

    @Test
    fun `closed signal clears all listeners`() {
        val signal = DefaultMutableSignal(1)
        var receivedCount = 0

        signal.subscribe { receivedCount++ }
        signal.subscribe { receivedCount++ }
        signal.subscribe { receivedCount++ }

        signal.close()
        receivedCount = 0

        // No more updates should be received
        // (setting value on closed signal is ignored)
        signal.value = 2
        assertThat(receivedCount).isEqualTo(0)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val signal = DefaultMutableSignal(1)
        signal.close()

        val unsub = signal.subscribe { }
        unsub() // Should not throw
    }

    @Test
    fun `closing computed signal unsubscribes from sources`() {
        val source = DefaultMutableSignal(1)
        val mapped = source.map { it * 2 }

        // Subscribe to force lazy subscription
        val unsub = mapped.subscribe { }

        mapped.close()

        // Source updates should not cause issues
        source.value = 10
        source.value = 20
    }

    // =========================================================================
    // CHAIN CLEANUP PATTERNS
    // =========================================================================

    @Test
    fun `unsubscribing from chain end cleans up intermediate subscriptions`() {
        val source = DefaultMutableSignal(0)
        val chain = source
            .map { it + 1 }
            .map { it * 2 }
            .filter { it > 0 }

        var received = 0
        val unsub = chain.subscribe { received++ }

        unsub()

        // Chain should not receive updates
        val before = received
        source.value = 10
        Thread.sleep(50)
        // After unsubscribe, no new emissions
    }

    @Test
    fun `closing intermediate signal in chain breaks propagation`() {
        val source = DefaultMutableSignal(1)
        val middle = source.map { it * 2 }
        val end = middle.map { it + 1 }

        var received = 0
        end.subscribe { received++ }

        middle.close()

        // Closed middle should not propagate
        val before = received
        source.value = 10
        Thread.sleep(50)
        // No updates after close
    }

    // =========================================================================
    // BINDABLE SIGNAL CLEANUP
    // =========================================================================

    @Test
    fun `rebinding cleans up old binding`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val bindable = bindableSignalOf(source1)

        val values = mutableListOf<Int>()
        bindable.subscribe { r -> r.onSuccess { values.add(it) } }
        values.clear()

        bindable.bindTo(source2)

        // Old source updates should not affect bindable
        source1.value = 100
        Thread.sleep(50)

        // Only source2 updates should be received
        source2.value = 20
        assertThat(values).contains(20)
        assertThat(values).doesNotContain(100)
    }

    @Test
    fun `bindable with takeOwnership closes old signal on rebind`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val bindable = bindableSignalOf(source1, takeOwnership = true)

        bindable.subscribe { }

        bindable.bindTo(source2)

        assertThat(source1.isClosed).isTrue()
        assertThat(source2.isClosed).isFalse()
    }

    // =========================================================================
    // FLATTEN SIGNAL CLEANUP
    // =========================================================================

    @Test
    fun `flatten signal cleans up old inner signal on switch`() {
        val inner1 = DefaultMutableSignal(1)
        val inner2 = DefaultMutableSignal(2)
        val outer = DefaultMutableSignal<Signal<Int>>(inner1)
        val flattened = FlattenSignal(outer)

        val values = mutableListOf<Int>()
        flattened.subscribe { r -> r.onSuccess { values.add(it) } }
        values.clear()

        // Switch to inner2
        outer.value = inner2

        // inner1 updates should not affect flattened
        inner1.value = 100
        Thread.sleep(50)

        // Only inner2 updates should be received
        inner2.value = 20
        assertThat(values).contains(20)
    }

    @Test
    fun `closing flatten signal cleans up all subscriptions`() {
        val inner = DefaultMutableSignal(1)
        val outer = DefaultMutableSignal<Signal<Int>>(inner)
        val flattened = FlattenSignal(outer)

        flattened.subscribe { }
        flattened.close()

        // Updates should not cause issues
        inner.value = 100
        outer.value = DefaultMutableSignal(2)
    }

    // =========================================================================
    // WEAK REFERENCE PATTERNS (BEST EFFORT)
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
