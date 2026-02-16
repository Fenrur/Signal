package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractBindableSignalThreadingTest
import io.github.fenrur.signal.AbstractSignalThreadingTest
import io.github.fenrur.signal.BindableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.signalOf

class DefaultBindableSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = signalOf(initial)
        return DefaultBindableSignal(source)
    }

    class BindableThreadingTests : AbstractBindableSignalThreadingTest<BindableSignal<Int>>() {

        override fun createUnboundSignal(): BindableSignal<Int> =
            DefaultBindableSignal()

        override fun createSignal(source: Signal<Int>): BindableSignal<Int> =
            DefaultBindableSignal(source)

        override fun bindTo(signal: BindableSignal<Int>, source: Signal<Int>) {
            signal.bindTo(source)
        }
    }
}
