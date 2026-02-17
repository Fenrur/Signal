package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalTest
import io.github.fenrur.signal.Signal

class DefaultSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> =
        DefaultSignal(initial)
}
