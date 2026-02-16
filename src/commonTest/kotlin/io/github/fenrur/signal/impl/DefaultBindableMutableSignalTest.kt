package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractBindableSignalTest
import io.github.fenrur.signal.AbstractMutableSignalTest
import io.github.fenrur.signal.BindableMutableSignal
import io.github.fenrur.signal.BindableSignal
import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal
import kotlin.test.*

class DefaultBindableMutableSignalTest : io.github.fenrur.signal.AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): io.github.fenrur.signal.MutableSignal<Int> {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(initial)
        return io.github.fenrur.signal.impl.DefaultBindableMutableSignal(source)
    }

    override fun createNullableSignal(): io.github.fenrur.signal.MutableSignal<Int?> {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(null)
        return io.github.fenrur.signal.impl.DefaultBindableMutableSignal(source)
    }

    // ==================== Bindable-specific test implementation ====================

    class BindableBehaviorTests : io.github.fenrur.signal.AbstractBindableSignalTest<io.github.fenrur.signal.BindableMutableSignal<Int>>() {

        override fun createUnboundSignal(): io.github.fenrur.signal.BindableMutableSignal<Int> =
            io.github.fenrur.signal.impl.DefaultBindableMutableSignal()

        override fun createSignal(source: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.BindableMutableSignal<Int> {
            val mutableSource = source as? io.github.fenrur.signal.MutableSignal<Int>
                ?: io.github.fenrur.signal.impl.DefaultMutableSignal(source.value)
            return io.github.fenrur.signal.impl.DefaultBindableMutableSignal(mutableSource)
        }

        override fun createSignal(source: io.github.fenrur.signal.Signal<Int>, takeOwnership: Boolean): io.github.fenrur.signal.BindableMutableSignal<Int> {
            val mutableSource = source as? io.github.fenrur.signal.MutableSignal<Int>
                ?: io.github.fenrur.signal.impl.DefaultMutableSignal(source.value)
            return io.github.fenrur.signal.impl.DefaultBindableMutableSignal(
                mutableSource,
                takeOwnership
            )
        }

        override fun bindTo(signal: io.github.fenrur.signal.BindableMutableSignal<Int>, source: io.github.fenrur.signal.Signal<Int>) {
            val mutableSource = source as? io.github.fenrur.signal.MutableSignal<Int>
                ?: io.github.fenrur.signal.impl.DefaultMutableSignal(source.value)
            signal.bindTo(mutableSource)
        }

        override fun isBound(signal: io.github.fenrur.signal.BindableMutableSignal<Int>): Boolean =
            signal.isBound()

        override fun currentSignal(signal: io.github.fenrur.signal.BindableMutableSignal<Int>): io.github.fenrur.signal.Signal<Int>? =
            signal.currentSignal()

        override fun wouldCreateCycle(signal: io.github.fenrur.signal.BindableMutableSignal<Int>, target: io.github.fenrur.signal.Signal<Int>): Boolean =
            io.github.fenrur.signal.BindableSignal.wouldCreateCycle(signal, target)
    }

    // ==================== DefaultBindableMutableSignal-specific tests ====================

    @Test
    fun `setting value updates the bound source`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val signal = io.github.fenrur.signal.impl.DefaultBindableMutableSignal(source)

        signal.value = 20

        assertEquals(20, source.value)
    }

    @Test
    fun `update updates the bound source`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val signal = io.github.fenrur.signal.impl.DefaultBindableMutableSignal(source)

        signal.update { it + 5 }

        assertEquals(15, source.value)
    }
}
