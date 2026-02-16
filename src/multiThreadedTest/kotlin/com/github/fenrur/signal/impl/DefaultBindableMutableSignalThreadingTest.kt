package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractBindableSignalThreadingTest
import com.github.fenrur.signal.AbstractMutableSignalThreadingTest
import com.github.fenrur.signal.BindableMutableSignal
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.Signal

class DefaultBindableMutableSignalThreadingTest : AbstractMutableSignalThreadingTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> {
        val source = DefaultMutableSignal(initial)
        return DefaultBindableMutableSignal(source)
    }

    class BindableThreadingTests : AbstractBindableSignalThreadingTest<BindableMutableSignal<Int>>() {

        override fun createUnboundSignal(): BindableMutableSignal<Int> =
            DefaultBindableMutableSignal()

        override fun createSignal(source: Signal<Int>): BindableMutableSignal<Int> {
            val mutableSource = source as? MutableSignal<Int> ?: DefaultMutableSignal(source.value)
            return DefaultBindableMutableSignal(mutableSource)
        }

        override fun bindTo(signal: BindableMutableSignal<Int>, source: Signal<Int>) {
            val mutableSource = source as? MutableSignal<Int> ?: DefaultMutableSignal(source.value)
            signal.bindTo(mutableSource)
        }
    }
}
