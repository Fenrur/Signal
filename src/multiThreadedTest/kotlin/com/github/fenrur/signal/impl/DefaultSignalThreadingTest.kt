package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalThreadingTest
import com.github.fenrur.signal.Signal

class DefaultSignalThreadingTest : AbstractSignalThreadingTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> = DefaultSignal(initial)
}
