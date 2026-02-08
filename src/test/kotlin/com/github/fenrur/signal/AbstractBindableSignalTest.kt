package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * Abstract base class for testing BindableSignal implementations.
 * Contains common tests for both read-only and mutable bindable signals.
 *
 * @param S The type of bindable signal being tested
 */
abstract class AbstractBindableSignalTest<S : Signal<Int>> {

    /**
     * Creates an unbound bindable signal for testing.
     */
    protected abstract fun createUnboundSignal(): S

    /**
     * Creates a bindable signal bound to the given source.
     */
    protected abstract fun createSignal(source: Signal<Int>): S

    /**
     * Creates a bindable signal bound to the given source with takeOwnership option.
     */
    protected abstract fun createSignal(source: Signal<Int>, takeOwnership: Boolean): S

    /**
     * Creates a mutable signal for use as a source.
     */
    protected fun createMutableSource(initial: Int): DefaultMutableSignal<Int> =
        DefaultMutableSignal(initial)

    /**
     * Binds the signal to a new source.
     */
    protected abstract fun bindTo(signal: S, source: Signal<Int>)

    /**
     * Returns whether the signal is bound.
     */
    protected abstract fun isBound(signal: S): Boolean

    /**
     * Returns the currently bound signal.
     */
    protected abstract fun currentSignal(signal: S): Signal<Int>?

    /**
     * Checks if binding would create a cycle.
     */
    protected abstract fun wouldCreateCycle(signal: S, target: Signal<Int>): Boolean

    // ==================== Unbound state tests ====================

    @Test
    fun `unbound signal throws on value access`() {
        val signal = createUnboundSignal()

        assertThatThrownBy { signal.value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not bound")
    }

    // ==================== bindTo tests ====================

    @Test
    fun `bindTo changes the underlying signal`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)

        assertThat(signal.value).isEqualTo(10)

        bindTo(signal, source2)

        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `bindTo notifies subscribers with new value`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        bindTo(signal, source2)

        assertThat(values).contains(20)
    }

    @Test
    fun `changes to new source are propagated`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        bindTo(signal, source2)
        values.clear()

        source2.value = 30

        assertThat(values).contains(30)
        assertThat(signal.value).isEqualTo(30)
    }

    @Test
    fun `changes to old source are not propagated after rebind`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        bindTo(signal, source2)
        values.clear()

        source1.value = 100

        assertThat(values).doesNotContain(100)
        assertThat(signal.value).isEqualTo(20)
    }

    // ==================== takeOwnership tests ====================

    @Test
    fun `takeOwnership closes old signal on rebind`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1, takeOwnership = true)

        assertThat(source1.isClosed).isFalse()

        bindTo(signal, source2)

        assertThat(source1.isClosed).isTrue()
        assertThat(source2.isClosed).isFalse()
    }

    @Test
    fun `takeOwnership closes source on close`() {
        val source = createMutableSource(10)
        val signal = createSignal(source, takeOwnership = true)

        signal.close()

        assertThat(source.isClosed).isTrue()
    }

    @Test
    fun `without takeOwnership source is not closed`() {
        val source = createMutableSource(10)
        val signal = createSignal(source, takeOwnership = false)

        signal.close()

        assertThat(source.isClosed).isFalse()
    }

    // ==================== isBound / currentSignal tests ====================

    @Test
    fun `isBound returns correct state`() {
        val signal = createUnboundSignal()
        assertThat(isBound(signal)).isFalse()

        bindTo(signal, createMutableSource(10))
        assertThat(isBound(signal)).isTrue()
    }

    @Test
    fun `currentSignal returns bound signal`() {
        val source = createMutableSource(10)
        val signal = createSignal(source)

        assertThat(currentSignal(signal)).isSameAs(source)
    }

    @Test
    fun `currentSignal returns null when not bound`() {
        val signal = createUnboundSignal()
        assertThat(currentSignal(signal)).isNull()
    }

    // ==================== Circular binding detection tests ====================

    @Test
    fun `wouldCreateCycle returns false for non-circular binding`() {
        val a = createSignal(createMutableSource(1))
        val b = createSignal(createMutableSource(2))

        assertThat(wouldCreateCycle(a, b)).isFalse()
    }

    @Test
    fun `wouldCreateCycle returns true for direct self-binding`() {
        val a = createSignal(createMutableSource(1))

        assertThat(wouldCreateCycle(a, a)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns true for simple cycle A to B to A`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()

        bindTo(b, a)

        assertThat(wouldCreateCycle(a, b)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns true for chain cycle A to B to C to A`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()
        val c = createUnboundSignal()

        bindTo(b, a)
        bindTo(c, b)

        assertThat(wouldCreateCycle(a, c)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns false for valid chain`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()
        val c = createUnboundSignal()

        bindTo(b, a)

        assertThat(wouldCreateCycle(c, a)).isFalse()
    }

    @Test
    fun `wouldCreateCycle returns false when target is regular Signal`() {
        val a = createSignal(createMutableSource(1))
        val b = signalOf(2)

        assertThat(wouldCreateCycle(a, b)).isFalse()
    }

    @Test
    fun `bindTo throws on direct self-binding`() {
        val a = createSignal(createMutableSource(1))

        assertThatThrownBy { bindTo(a, a) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo throws on simple cycle`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()

        bindTo(b, a)

        assertThatThrownBy { bindTo(a, b) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo throws on chain cycle`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()
        val c = createUnboundSignal()

        bindTo(b, a)
        bindTo(c, b)

        assertThatThrownBy { bindTo(a, c) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo allows valid rebinding without cycle`() {
        val a = createSignal(createMutableSource(1))
        val b = createSignal(createMutableSource(2))
        val c = createSignal(createMutableSource(3))

        bindTo(a, b)
        bindTo(a, c)

        assertThat(a.value).isEqualTo(3)
    }

    // ==================== Multiple successive rebindings ====================

    @Test
    fun `multiple successive rebindings work correctly`() {
        val signals = (1..5).map { createMutableSource(it * 10) }
        val bindable = createSignal(signals[0])
        val values = CopyOnWriteArrayList<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }

        // Rebind through all signals
        for (i in 1 until signals.size) {
            values.clear()
            bindTo(bindable, signals[i])
            assertThat(bindable.value).isEqualTo((i + 1) * 10)
            assertThat(values).contains((i + 1) * 10)
        }

        // Only last signal affects bindable
        values.clear()
        signals[0].value = 999
        signals[1].value = 888
        signals[2].value = 777
        signals[3].value = 666
        signals[4].value = 555

        assertThat(values).containsExactly(555)
        assertThat(bindable.value).isEqualTo(555)
    }

    @Test
    fun `rebinding to same signal is idempotent`() {
        val source = createMutableSource(10)
        val bindable = createSignal(source)
        val values = CopyOnWriteArrayList<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        // Rebind to same signal multiple times
        bindTo(bindable, source)
        bindTo(bindable, source)
        bindTo(bindable, source)

        source.value = 20
        assertThat(bindable.value).isEqualTo(20)
        // Each rebind notifies, so we get multiple notifications for 10
        assertThat(values).contains(20)
    }

    @Test
    fun `rapid rebinding does not lose updates`() {
        val signals = (1..10).map { createMutableSource(it) }
        val bindable = createSignal(signals[0])
        val values = CopyOnWriteArrayList<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }

        // Rapidly rebind
        signals.forEach { bindTo(bindable, it) }

        // Final value should be from last signal
        assertThat(bindable.value).isEqualTo(10)

        // Update last signal
        values.clear()
        signals.last().value = 100
        assertThat(values).containsExactly(100)
    }

    @Test
    fun `rebinding clears dirty flag correctly`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val bindable = createSignal(source1)

        // Read to make it clean
        assertThat(bindable.value).isEqualTo(10)

        // Update source1 (makes bindable dirty)
        source1.value = 15

        // Rebind before reading dirty value
        bindTo(bindable, source2)

        // Should return source2's value, not stale source1 value
        assertThat(bindable.value).isEqualTo(20)
    }

    @Test
    fun `concurrent rebinding is thread-safe`() {
        val signals = (1..10).map { createMutableSource(it * 10) }
        val bindable = createSignal(signals[0])
        val latch = CountDownLatch(1)
        val threads = mutableListOf<Thread>()

        bindable.subscribe { }

        // Multiple threads rebinding
        repeat(5) { threadId ->
            threads += Thread {
                latch.await()
                repeat(20) { i ->
                    val idx = (threadId * 20 + i) % signals.size
                    bindTo(bindable, signals[idx])
                    Thread.yield()
                }
            }
        }

        // Thread updating signals
        threads += Thread {
            latch.await()
            repeat(100) { i ->
                signals.forEach { it.value = i * 100 }
                Thread.yield()
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }

        // Bindable is still functional
        val finalSignal = createMutableSource(999)
        bindTo(bindable, finalSignal)
        assertThat(bindable.value).isEqualTo(999)
    }
}
