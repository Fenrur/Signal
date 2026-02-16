package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalThreadingTest
import com.github.fenrur.signal.Signal

class MappedSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = DefaultMutableSignal(initial)
        return MappedSignal(source) { it }
    }
}
