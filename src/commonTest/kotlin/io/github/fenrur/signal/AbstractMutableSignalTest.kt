package io.github.fenrur.signal

import kotlin.test.*

/**
 * Abstract test class for [io.github.fenrur.signal.MutableSignal] interface.
 * Contains non-threading tests that can run on all platforms.
 * Extends [io.github.fenrur.signal.AbstractSignalTest] to inherit all Signal tests.
 */
abstract class AbstractMutableSignalTest : AbstractSignalTest<MutableSignal<Int>>() {

    abstract override fun createSignal(initial: Int): MutableSignal<Int>

    // ==================== value setter ====================

    @Test
    fun `setting value updates the signal`() {
        val signal = createSignal(42)
        signal.value = 100
        assertEquals(100, signal.value)
    }

    @Test
    fun `setting value notifies subscribers`() {
        val signal = createSignal(42)
        val values = mutableListOf<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }

        signal.value = 100
        signal.value = 200

        assertEquals(listOf(42, 100, 200), values)
    }

    @Test
    fun `setting same value does not notify subscribers`() {
        val signal = createSignal(42)
        var callCount = 0

        signal.subscribe { callCount++ }

        signal.value = 42
        signal.value = 42

        assertEquals(1, callCount)
    }

    @Test
    fun `setting value on closed signal does nothing`() {
        val signal = createSignal(42)
        signal.close()

        signal.value = 100
    }

    @Test
    fun `setting value does not notify unsubscribed listeners`() {
        val signal = createSignal(42)
        val values = mutableListOf<Int>()

        val unsubscribe = signal.subscribe { it.onSuccess { v -> values.add(v) } }

        signal.value = 100
        unsubscribe()
        signal.value = 200

        assertEquals(listOf(42, 100), values)
    }

    // ==================== update ====================

    @Test
    fun `update transforms current value`() {
        val signal = createSignal(42)
        signal.update { it + 10 }
        assertEquals(52, signal.value)
    }

    @Test
    fun `update notifies subscribers`() {
        val signal = createSignal(42)
        val values = mutableListOf<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }

        signal.update { it * 2 }

        assertEquals(listOf(42, 84), values)
    }

    @Test
    fun `update with same value does not notify`() {
        val signal = createSignal(42)
        var callCount = 0

        signal.subscribe { callCount++ }

        signal.update { it }

        assertEquals(1, callCount)
    }

    @Test
    fun `update on closed signal does nothing`() {
        val signal = createSignal(42)
        signal.close()

        signal.update { it + 10 }
    }

    // ==================== setValue (property delegate) ====================

    @Test
    fun `setValue updates the signal`() {
        val signal = createSignal(42)
        var prop: Int by signal
        prop = 100
        assertEquals(100, signal.value)
    }

    @Test
    fun `property delegate read and write`() {
        val signal = createSignal(42)
        var prop: Int by signal

        assertEquals(42, prop)
        prop = 100
        assertEquals(100, prop)
        assertEquals(100, signal.value)
    }

    // ==================== Notification order ====================

    protected open fun preservesSubscriptionOrder(): Boolean = true

    @Test
    fun `subscribers are notified in subscription order`() {
        if (!preservesSubscriptionOrder()) return

        val signal = createSignal(0)
        val order = mutableListOf<String>()

        signal.subscribe { order.add("first") }
        signal.subscribe { order.add("second") }
        signal.subscribe { order.add("third") }

        order.clear()

        signal.value = 1

        assertEquals(listOf("first", "second", "third"), order)
    }

    // ==================== Edge cases ====================

    @Test
    fun `null value is supported for nullable types`() {
        val signal = createNullableSignal()
        assertNull(signal.value)

        signal.value = 42
        assertEquals(42, signal.value)

        signal.value = null
        assertNull(signal.value)
    }

    protected open fun createNullableSignal(): MutableSignal<Int?> {
        @Suppress("UNCHECKED_CAST")
        return createSignal(0) as MutableSignal<Int?>
    }
}
