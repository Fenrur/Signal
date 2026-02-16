package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalThreadingTest
import com.github.fenrur.signal.MutableSignal

class DefaultMutableSignalThreadingTest : AbstractMutableSignalThreadingTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> = DefaultMutableSignal(initial)
}
