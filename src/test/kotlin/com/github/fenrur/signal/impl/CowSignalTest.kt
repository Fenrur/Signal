package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.MutableSignal

class CowSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> = CowSignal(initial)

    override fun createNullableSignal(): MutableSignal<Int?> = CowSignal(null)
}
