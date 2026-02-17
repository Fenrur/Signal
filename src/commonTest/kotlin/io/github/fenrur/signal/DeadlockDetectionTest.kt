package io.github.fenrur.signal

import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.operators.combine
import kotlin.test.*

/**
 * Tests for deadlock detection and prevention.
 * Verifies that circular bindings are detected and rejected,
 * and that valid binding patterns work correctly.
 */
class DeadlockDetectionTest {

    // =========================================================================
    // CYCLE DETECTION IN BINDABLE SIGNALS
    // =========================================================================

    @Test
    fun `direct self-binding is rejected`() {
        val source = DefaultMutableSignal(1)
        val signal = io.github.fenrur.signal.bindableSignalOf(source)

        // Cannot bind to self
        val ex = assertFailsWith<IllegalStateException> {
            signal.bindTo(signal)
        }
        assertTrue(ex.message?.contains("cycle") == true)
    }

    @Test
    fun `two-signal cycle is rejected`() {
        val source = DefaultMutableSignal(1)
        val a = io.github.fenrur.signal.bindableSignalOf(source)
        val b = io.github.fenrur.signal.bindableSignalOf(a)  // b -> a

        // a -> b would create cycle (a -> b -> a)
        val ex = assertFailsWith<IllegalStateException> {
            a.bindTo(b)
        }
        assertTrue(ex.message?.contains("cycle") == true)
    }

    @Test
    fun `three-signal cycle is rejected`() {
        val source = DefaultMutableSignal(1)
        val a = io.github.fenrur.signal.bindableSignalOf(source)
        val b = io.github.fenrur.signal.bindableSignalOf(a)  // b -> a
        val c = io.github.fenrur.signal.bindableSignalOf(b)  // c -> b -> a

        // a -> c would create cycle (a -> c -> b -> a)
        val ex = assertFailsWith<IllegalStateException> {
            a.bindTo(c)
        }
        assertTrue(ex.message?.contains("cycle") == true)
    }

    @Test
    fun `long chain cycle is rejected`() {
        val source = DefaultMutableSignal(1)

        // Create chain: source <- s0 <- s1 <- s2 <- ... <- s9
        val signals = mutableListOf<BindableSignal<Int>>()
        signals.add(io.github.fenrur.signal.bindableSignalOf(source))
        for (i in 1 until 10) {
            signals.add(io.github.fenrur.signal.bindableSignalOf(signals[i - 1]))
        }

        // Binding s0 -> s9 would create cycle
        val ex = assertFailsWith<IllegalStateException> {
            signals[0].bindTo(signals[9])
        }
        assertTrue(ex.message?.contains("cycle") == true)
    }

    @Test
    fun `mutable bindable self-binding is rejected`() {
        val source = DefaultMutableSignal(1)
        val signal = io.github.fenrur.signal.bindableMutableSignalOf(source)

        val ex = assertFailsWith<IllegalStateException> {
            signal.bindTo(signal)
        }
        assertTrue(ex.message?.contains("cycle") == true)
    }

    @Test
    fun `mutable bindable two-signal cycle is rejected`() {
        val source = DefaultMutableSignal(1)
        val a = io.github.fenrur.signal.bindableMutableSignalOf(source)
        val b = io.github.fenrur.signal.bindableMutableSignalOf(a)  // b -> a

        val ex = assertFailsWith<IllegalStateException> {
            a.bindTo(b)
        }
        assertTrue(ex.message?.contains("cycle") == true)
    }

    // =========================================================================
    // VALID BINDING PATTERNS (NO CYCLE)
    // =========================================================================

    @Test
    fun `linear chain is valid`() {
        val source = DefaultMutableSignal(1)
        val a = io.github.fenrur.signal.bindableSignalOf(source)
        val b = io.github.fenrur.signal.bindableSignalOf(a)
        val c = io.github.fenrur.signal.bindableSignalOf(b)

        assertEquals(1, c.value)

        source.value = 42
        assertEquals(42, c.value)
    }

    @Test
    fun `rebinding to different signal is valid`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val bindable = io.github.fenrur.signal.bindableSignalOf(source1)

        assertEquals(1, bindable.value)

        bindable.bindTo(source2)
        assertEquals(2, bindable.value)
    }

    @Test
    fun `diamond without cycle is valid`() {
        //     source
        //      / \
        //     a   b
        //      \ /
        //       c (non-bindable combine, not cycle)
        val source = DefaultMutableSignal(1)
        val a = io.github.fenrur.signal.bindableSignalOf(source)
        val b = io.github.fenrur.signal.bindableSignalOf(source)
        val c = combine(a, b) { x, y -> x + y }

        assertEquals(2, c.value)

        source.value = 10
        assertEquals(20, c.value)
    }

    @Test
    fun `switching binding target is valid`() {
        val source1 = DefaultMutableSignal(1)
        val source2 = DefaultMutableSignal(2)
        val a = io.github.fenrur.signal.bindableSignalOf(source1)
        val b = io.github.fenrur.signal.bindableSignalOf(source2)

        // Switch a to point to source2
        a.bindTo(source2)
        assertEquals(2, a.value)

        // b can still bind to source1
        b.bindTo(source1)
        assertEquals(1, b.value)
    }
}
