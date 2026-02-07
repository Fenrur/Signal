package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalTest
import com.github.fenrur.signal.Signal

class CowSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> = CowSignal(initial)
}
