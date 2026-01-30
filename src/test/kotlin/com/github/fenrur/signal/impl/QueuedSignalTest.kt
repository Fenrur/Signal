package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.MutableSignal

class QueuedSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> = QueuedSignal(initial)

    override fun createNullableSignal(): MutableSignal<Int?> = QueuedSignal(null)
}
