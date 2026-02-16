package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalThreadingTest
import com.github.fenrur.signal.Signal

class FlattenSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val inner = DefaultMutableSignal(initial)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        return FlattenSignal(outer)
    }
}
