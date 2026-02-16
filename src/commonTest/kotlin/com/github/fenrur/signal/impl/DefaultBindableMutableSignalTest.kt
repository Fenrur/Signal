package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractBindableSignalTest
import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.BindableMutableSignal
import com.github.fenrur.signal.BindableSignal
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.Signal
import kotlin.test.*

class DefaultBindableMutableSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> {
        val source = DefaultMutableSignal(initial)
        return DefaultBindableMutableSignal(source)
    }

    override fun createNullableSignal(): MutableSignal<Int?> {
        val source = DefaultMutableSignal<Int?>(null)
        return DefaultBindableMutableSignal(source)
    }

    // ==================== Bindable-specific test implementation ====================

    class BindableBehaviorTests : AbstractBindableSignalTest<BindableMutableSignal<Int>>() {

        override fun createUnboundSignal(): BindableMutableSignal<Int> =
            DefaultBindableMutableSignal()

        override fun createSignal(source: Signal<Int>): BindableMutableSignal<Int> {
            val mutableSource = source as? MutableSignal<Int> ?: DefaultMutableSignal(source.value)
            return DefaultBindableMutableSignal(mutableSource)
        }

        override fun createSignal(source: Signal<Int>, takeOwnership: Boolean): BindableMutableSignal<Int> {
            val mutableSource = source as? MutableSignal<Int> ?: DefaultMutableSignal(source.value)
            return DefaultBindableMutableSignal(mutableSource, takeOwnership)
        }

        override fun bindTo(signal: BindableMutableSignal<Int>, source: Signal<Int>) {
            val mutableSource = source as? MutableSignal<Int> ?: DefaultMutableSignal(source.value)
            signal.bindTo(mutableSource)
        }

        override fun isBound(signal: BindableMutableSignal<Int>): Boolean =
            signal.isBound()

        override fun currentSignal(signal: BindableMutableSignal<Int>): Signal<Int>? =
            signal.currentSignal()

        override fun wouldCreateCycle(signal: BindableMutableSignal<Int>, target: Signal<Int>): Boolean =
            BindableSignal.wouldCreateCycle(signal, target)
    }

    // ==================== DefaultBindableMutableSignal-specific tests ====================

    @Test
    fun `setting value updates the bound source`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source)

        signal.value = 20

        assertEquals(20, source.value)
    }

    @Test
    fun `update updates the bound source`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source)

        signal.update { it + 5 }

        assertEquals(15, source.value)
    }
}
