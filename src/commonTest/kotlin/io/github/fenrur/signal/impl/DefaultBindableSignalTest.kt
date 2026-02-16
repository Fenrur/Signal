package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractBindableSignalTest
import io.github.fenrur.signal.AbstractSignalTest
import io.github.fenrur.signal.BindableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.signalOf
import kotlin.test.*

class DefaultBindableSignalTest : io.github.fenrur.signal.AbstractSignalTest<io.github.fenrur.signal.Signal<Int>>() {

    override fun createSignal(initial: Int): io.github.fenrur.signal.Signal<Int> {
        val source = io.github.fenrur.signal.signalOf(initial)
        return io.github.fenrur.signal.impl.DefaultBindableSignal(source)
    }

    // ==================== Bindable-specific test implementation ====================

    class BindableBehaviorTests : io.github.fenrur.signal.AbstractBindableSignalTest<io.github.fenrur.signal.BindableSignal<Int>>() {

        override fun createUnboundSignal(): io.github.fenrur.signal.BindableSignal<Int> =
            io.github.fenrur.signal.impl.DefaultBindableSignal()

        override fun createSignal(source: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.BindableSignal<Int> =
            io.github.fenrur.signal.impl.DefaultBindableSignal(source)

        override fun createSignal(source: io.github.fenrur.signal.Signal<Int>, takeOwnership: Boolean): io.github.fenrur.signal.BindableSignal<Int> =
            io.github.fenrur.signal.impl.DefaultBindableSignal(source, takeOwnership)

        override fun bindTo(signal: io.github.fenrur.signal.BindableSignal<Int>, source: io.github.fenrur.signal.Signal<Int>) {
            signal.bindTo(source)
        }

        override fun isBound(signal: io.github.fenrur.signal.BindableSignal<Int>): Boolean =
            signal.isBound()

        override fun currentSignal(signal: io.github.fenrur.signal.BindableSignal<Int>): io.github.fenrur.signal.Signal<Int>? =
            signal.currentSignal()

        override fun wouldCreateCycle(signal: io.github.fenrur.signal.BindableSignal<Int>, target: io.github.fenrur.signal.Signal<Int>): Boolean =
            io.github.fenrur.signal.BindableSignal.wouldCreateCycle(signal, target)
    }

    // ==================== DefaultBindableSignal-specific tests ====================

    @Test
    fun `can bind to read-only signal`() {
        val readOnly = io.github.fenrur.signal.signalOf(42)
        val signal = io.github.fenrur.signal.impl.DefaultBindableSignal(readOnly)

        assertEquals(42, signal.value)
    }

    @Test
    fun `bindable signal reflects source value without write capability`() {
        val source = io.github.fenrur.signal.mutableSignalOf(10)
        val signal = io.github.fenrur.signal.impl.DefaultBindableSignal(source)

        assertEquals(10, signal.value)

        source.value = 20
        assertEquals(20, signal.value)
    }

    @Test
    fun `toString shows value and state`() {
        val signal = io.github.fenrur.signal.impl.DefaultBindableSignal(
            io.github.fenrur.signal.signalOf(42)
        )
        assertTrue(signal.toString().contains("42"))
        assertTrue(signal.toString().contains("DefaultBindableSignal"))
    }

    @Test
    fun `toString shows not bound when unbound`() {
        val signal = io.github.fenrur.signal.impl.DefaultBindableSignal<Int>()
        assertTrue(signal.toString().contains("<not bound>"))
    }
}
