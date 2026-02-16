package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractBindableSignalThreadingTest
import io.github.fenrur.signal.AbstractMutableSignalThreadingTest
import io.github.fenrur.signal.BindableMutableSignal
import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal

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
