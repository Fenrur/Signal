package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalThreadingTest
import io.github.fenrur.signal.Signal

class FlattenSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val inner = DefaultMutableSignal(initial)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        return FlattenSignal(outer)
    }
}
