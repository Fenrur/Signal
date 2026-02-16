package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalThreadingTest
import io.github.fenrur.signal.Signal

class DefaultSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> = DefaultSignal(initial)
}
