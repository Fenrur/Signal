package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractBindableSignalTest
import com.github.fenrur.signal.AbstractSignalTest
import com.github.fenrur.signal.BindableSignal
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.mutableSignalOf
import com.github.fenrur.signal.signalOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for DefaultBindableSignal.
 * Extends both AbstractSignalTest (for Signal interface compliance) and
 * AbstractBindableSignalTest (for bindable-specific behavior).
 */
class DefaultBindableSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = signalOf(initial)
        return DefaultBindableSignal(source)
    }

    // ==================== Bindable-specific test implementation ====================

    /**
     * Inner test class that inherits common bindable signal tests.
     */
    class BindableBehaviorTests : AbstractBindableSignalTest<BindableSignal<Int>>() {

        override fun createUnboundSignal(): BindableSignal<Int> =
            DefaultBindableSignal()

        override fun createSignal(source: Signal<Int>): BindableSignal<Int> =
            DefaultBindableSignal(source)

        override fun createSignal(source: Signal<Int>, takeOwnership: Boolean): BindableSignal<Int> =
            DefaultBindableSignal(source, takeOwnership)

        override fun bindTo(signal: BindableSignal<Int>, source: Signal<Int>) {
            signal.bindTo(source)
        }

        override fun isBound(signal: BindableSignal<Int>): Boolean =
            signal.isBound()

        override fun currentSignal(signal: BindableSignal<Int>): Signal<Int>? =
            signal.currentSignal()

        override fun wouldCreateCycle(signal: BindableSignal<Int>, target: Signal<Int>): Boolean =
            BindableSignal.wouldCreateCycle(signal, target)
    }

    // ==================== DefaultBindableSignal-specific tests ====================

    @Test
    fun `can bind to read-only signal`() {
        val readOnly = signalOf(42)
        val signal = DefaultBindableSignal(readOnly)

        assertThat(signal.value).isEqualTo(42)
    }

    @Test
    fun `bindable signal reflects source value without write capability`() {
        val source = mutableSignalOf(10)
        val signal = DefaultBindableSignal(source)

        assertThat(signal.value).isEqualTo(10)

        source.value = 20
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `toString shows value and state`() {
        val signal = DefaultBindableSignal(signalOf(42))
        assertThat(signal.toString()).contains("42")
        assertThat(signal.toString()).contains("DefaultBindableSignal")
    }

    @Test
    fun `toString shows not bound when unbound`() {
        val signal = DefaultBindableSignal<Int>()
        assertThat(signal.toString()).contains("<not bound>")
    }
}
