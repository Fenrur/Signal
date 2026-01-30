package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.MutableSignal

class LockedSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> = LockedSignal(initial)

    override fun createNullableSignal(): MutableSignal<Int?> = LockedSignal(null)
}
