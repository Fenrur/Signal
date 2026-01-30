package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.MutableSignal

class IndexedSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> = IndexedSignal(initial)

    override fun createNullableSignal(): MutableSignal<Int?> = IndexedSignal(null)

    // IndexedSignal uses ConcurrentHashMap.newKeySet() which doesn't preserve order
    override fun preservesSubscriptionOrder(): Boolean = false
}
