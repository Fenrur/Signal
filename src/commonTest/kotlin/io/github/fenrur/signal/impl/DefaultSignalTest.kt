package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalTest
import io.github.fenrur.signal.Signal

class DefaultSignalTest : io.github.fenrur.signal.AbstractSignalTest<io.github.fenrur.signal.Signal<Int>>() {

    override fun createSignal(initial: Int): io.github.fenrur.signal.Signal<Int> =
        io.github.fenrur.signal.impl.DefaultSignal(initial)
}
