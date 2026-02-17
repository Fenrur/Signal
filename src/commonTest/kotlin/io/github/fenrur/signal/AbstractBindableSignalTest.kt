package io.github.fenrur.signal

import io.github.fenrur.signal.impl.DefaultMutableSignal
import kotlin.test.*

/**
 * Abstract base class for testing BindableSignal implementations.
 * Contains non-threading tests that can run on all platforms.
 *
 * @param S The type of bindable signal being tested
 */
abstract class AbstractBindableSignalTest<S : Signal<Int>> {

    protected abstract fun createUnboundSignal(): S

    protected abstract fun createSignal(source: Signal<Int>): S

    protected abstract fun createSignal(source: Signal<Int>, takeOwnership: Boolean): S

    protected fun createMutableSource(initial: Int): DefaultMutableSignal<Int> =
        DefaultMutableSignal(initial)

    protected abstract fun bindTo(signal: S, source: Signal<Int>)

    protected abstract fun isBound(signal: S): Boolean

    protected abstract fun currentSignal(signal: S): Signal<Int>?

    protected abstract fun wouldCreateCycle(signal: S, target: Signal<Int>): Boolean

    // ==================== Unbound state tests ====================

    @Test
    fun `unbound signal throws on value access`() {
        val signal = createUnboundSignal()

        assertFailsWith<IllegalStateException> { signal.value }
    }

    // ==================== bindTo tests ====================

    @Test
    fun `bindTo changes the underlying signal`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)

        assertEquals(10, signal.value)

        bindTo(signal, source2)

        assertEquals(20, signal.value)
    }

    @Test
    fun `bindTo notifies subscribers with new value`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)
        val values = mutableListOf<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        bindTo(signal, source2)

        assertTrue(values.contains(20))
    }

    @Test
    fun `changes to new source are propagated`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)
        val values = mutableListOf<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        bindTo(signal, source2)
        values.clear()

        source2.value = 30

        assertTrue(values.contains(30))
        assertEquals(30, signal.value)
    }

    @Test
    fun `changes to old source are not propagated after rebind`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1)
        val values = mutableListOf<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        bindTo(signal, source2)
        values.clear()

        source1.value = 100

        assertFalse(values.contains(100))
        assertEquals(20, signal.value)
    }

    // ==================== takeOwnership tests ====================

    @Test
    fun `takeOwnership closes old signal on rebind`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val signal = createSignal(source1, takeOwnership = true)

        assertFalse(source1.isClosed)

        bindTo(signal, source2)

        assertTrue(source1.isClosed)
        assertFalse(source2.isClosed)
    }

    @Test
    fun `takeOwnership closes source on close`() {
        val source = createMutableSource(10)
        val signal = createSignal(source, takeOwnership = true)

        signal.close()

        assertTrue(source.isClosed)
    }

    @Test
    fun `without takeOwnership source is not closed`() {
        val source = createMutableSource(10)
        val signal = createSignal(source, takeOwnership = false)

        signal.close()

        assertFalse(source.isClosed)
    }

    // ==================== isBound / currentSignal tests ====================

    @Test
    fun `isBound returns correct state`() {
        val signal = createUnboundSignal()
        assertFalse(isBound(signal))

        bindTo(signal, createMutableSource(10))
        assertTrue(isBound(signal))
    }

    @Test
    fun `currentSignal returns bound signal`() {
        val source = createMutableSource(10)
        val signal = createSignal(source)

        assertSame(source, currentSignal(signal))
    }

    @Test
    fun `currentSignal returns null when not bound`() {
        val signal = createUnboundSignal()
        assertNull(currentSignal(signal))
    }

    // ==================== Circular binding detection tests ====================

    @Test
    fun `wouldCreateCycle returns false for non-circular binding`() {
        val a = createSignal(createMutableSource(1))
        val b = createSignal(createMutableSource(2))

        assertFalse(wouldCreateCycle(a, b))
    }

    @Test
    fun `wouldCreateCycle returns true for direct self-binding`() {
        val a = createSignal(createMutableSource(1))

        assertTrue(wouldCreateCycle(a, a))
    }

    @Test
    fun `wouldCreateCycle returns true for simple cycle A to B to A`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()

        bindTo(b, a)

        assertTrue(wouldCreateCycle(a, b))
    }

    @Test
    fun `wouldCreateCycle returns true for chain cycle A to B to C to A`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()
        val c = createUnboundSignal()

        bindTo(b, a)
        bindTo(c, b)

        assertTrue(wouldCreateCycle(a, c))
    }

    @Test
    fun `wouldCreateCycle returns false for valid chain`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()
        val c = createUnboundSignal()

        bindTo(b, a)

        assertFalse(wouldCreateCycle(c, a))
    }

    @Test
    fun `wouldCreateCycle returns false when target is regular Signal`() {
        val a = createSignal(createMutableSource(1))
        val b = signalOf(2)

        assertFalse(wouldCreateCycle(a, b))
    }

    @Test
    fun `bindTo throws on direct self-binding`() {
        val a = createSignal(createMutableSource(1))

        assertFailsWith<IllegalStateException> { bindTo(a, a) }
    }

    @Test
    fun `bindTo throws on simple cycle`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()

        bindTo(b, a)

        assertFailsWith<IllegalStateException> { bindTo(a, b) }
    }

    @Test
    fun `bindTo throws on chain cycle`() {
        val a = createSignal(createMutableSource(1))
        val b = createUnboundSignal()
        val c = createUnboundSignal()

        bindTo(b, a)
        bindTo(c, b)

        assertFailsWith<IllegalStateException> { bindTo(a, c) }
    }

    @Test
    fun `bindTo allows valid rebinding without cycle`() {
        val a = createSignal(createMutableSource(1))
        val b = createSignal(createMutableSource(2))
        val c = createSignal(createMutableSource(3))

        bindTo(a, b)
        bindTo(a, c)

        assertEquals(3, a.value)
    }

    // ==================== Multiple successive rebindings ====================

    @Test
    fun `multiple successive rebindings work correctly`() {
        val signals = (1..5).map { createMutableSource(it * 10) }
        val bindable = createSignal(signals[0])
        val values = mutableListOf<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }

        for (i in 1 until signals.size) {
            values.clear()
            bindTo(bindable, signals[i])
            assertEquals((i + 1) * 10, bindable.value)
            assertTrue(values.contains((i + 1) * 10))
        }

        values.clear()
        signals[0].value = 999
        signals[1].value = 888
        signals[2].value = 777
        signals[3].value = 666
        signals[4].value = 555

        assertEquals(listOf(555), values)
        assertEquals(555, bindable.value)
    }

    @Test
    fun `rebinding to same signal is idempotent`() {
        val source = createMutableSource(10)
        val bindable = createSignal(source)
        val values = mutableListOf<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        bindTo(bindable, source)
        bindTo(bindable, source)
        bindTo(bindable, source)

        source.value = 20
        assertEquals(20, bindable.value)
        assertTrue(values.contains(20))
    }

    @Test
    fun `rapid rebinding does not lose updates`() {
        val signals = (1..10).map { createMutableSource(it) }
        val bindable = createSignal(signals[0])
        val values = mutableListOf<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }

        signals.forEach { bindTo(bindable, it) }

        assertEquals(10, bindable.value)

        values.clear()
        signals.last().value = 100
        assertEquals(listOf(100), values)
    }

    @Test
    fun `rebinding clears dirty flag correctly`() {
        val source1 = createMutableSource(10)
        val source2 = createMutableSource(20)
        val bindable = createSignal(source1)

        assertEquals(10, bindable.value)

        source1.value = 15

        bindTo(bindable, source2)

        assertEquals(20, bindable.value)
    }
}
