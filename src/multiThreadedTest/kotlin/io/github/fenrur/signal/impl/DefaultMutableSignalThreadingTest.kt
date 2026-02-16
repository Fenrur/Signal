package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractMutableSignalThreadingTest
import io.github.fenrur.signal.MutableSignal

class DefaultMutableSignalThreadingTest : AbstractMutableSignalThreadingTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> = DefaultMutableSignal(initial)
}
