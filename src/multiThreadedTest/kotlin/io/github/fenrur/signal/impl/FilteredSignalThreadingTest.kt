package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalThreadingTest
import io.github.fenrur.signal.Signal

class FilteredSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = DefaultMutableSignal(initial)
        return FilteredSignal(source) { it > 0 }
    }
}
